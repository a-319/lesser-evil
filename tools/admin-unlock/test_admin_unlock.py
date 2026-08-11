#!/usr/bin/env python3
"""Tests for the admin side of the unlock codes.

    python3 -m unittest discover tools/admin-unlock

The keys here are generated on the spot and never written down: no private
key belongs in this repository.
"""

import os
import stat
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import admin_unlock as au  # noqa: E402
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey  # noqa: E402

# The same case is checked in app/src/test/java/lesser/evil/AdminUnlockCodeTest.kt.
# If either side changes what it signs or how it encodes, both fail.
VECTOR_PUBLIC_KEY = "8aa40c022b9d23c94747a42954232b388d61f2d0cc81c3c7c2354c03c218544b"
VECTOR_CHALLENGE = "3074185296307418529630741852963074185296"
VECTOR_CHALLENGE_SHOWN = "307418 529630 741852 963074 185296 307418 529646"
VECTOR_RESPONSE = (
    "1219562025667449644456825642862157172465453292791854027600665581906941455818"
    "552841252729217344427282614312072316000203406100070332131898703974576235009511387"
)
VECTOR_CANONICAL = (
    "00013100166c65737365722e6576696c2e6170702d756e6c6f636b0028"
    "33303734313835323936333037343138353239363330373431383532393633303734313835323936"
)


def tamper(digits: str, index: int) -> str:
    replacement = "3" if digits[index] == "7" else "7"
    return digits[:index] + replacement + digits[index + 1:]


class ProtocolTest(unittest.TestCase):
    def setUp(self):
        self.key = Ed25519PrivateKey.generate()
        self.public = self.key.public_key()
        self.challenge = VECTOR_CHALLENGE

    def test_signature_over_the_current_challenge_verifies(self):
        response = au.sign_challenge(self.key, self.challenge)
        self.assertTrue(au.verify_response(self.public, self.challenge, response))

    def test_response_is_all_digits_and_full_length(self):
        response = au.sign_challenge(self.key, self.challenge)
        self.assertTrue(response.isdigit())
        self.assertEqual(au.RESPONSE_DIGITS + au.CHECK_DIGITS, len(response))

    def test_response_may_be_typed_with_any_separators(self):
        response = au.sign_challenge(self.key, self.challenge)
        spaced = " ".join(response[i:i + 5] for i in range(0, len(response), 5))
        self.assertTrue(au.verify_response(self.public, self.challenge, spaced))

    def test_signature_over_another_challenge_fails(self):
        other = "9" * au.CHALLENGE_DIGITS
        response = au.sign_challenge(self.key, other)
        self.assertFalse(au.verify_response(self.public, self.challenge, response))

    def test_changed_digit_in_challenge_fails(self):
        response = au.sign_challenge(self.key, self.challenge)
        self.assertFalse(au.verify_response(self.public, tamper(self.challenge, 0), response))
        self.assertFalse(au.verify_response(self.public, tamper(self.challenge, 39), response))

    def test_changed_digit_in_response_fails(self):
        response = au.sign_challenge(self.key, self.challenge)
        for index in (0, 80, au.RESPONSE_DIGITS - 1):
            broken = tamper(response, index)
            self.assertFalse(au.verify_response(self.public, self.challenge, broken))
            payload = broken[:au.RESPONSE_DIGITS]
            repaired = payload + au.check_digits(payload)
            self.assertFalse(au.verify_response(self.public, self.challenge, repaired))

    def test_signature_from_another_private_key_fails(self):
        response = au.sign_challenge(Ed25519PrivateKey.generate(), self.challenge)
        self.assertFalse(au.verify_response(self.public, self.challenge, response))

    def test_damaged_or_missing_input_fails(self):
        response = au.sign_challenge(self.key, self.challenge)
        for broken in ("", "   ", "not digits", response[:-1], response + "0", "9" * len(response)):
            self.assertFalse(au.verify_response(self.public, self.challenge, broken))

    def test_a_response_too_large_to_be_a_signature_fails(self):
        payload = "9" * au.RESPONSE_DIGITS  # above 2**512, so it holds no signature
        oversized = payload + au.check_digits(payload)
        self.assertFalse(au.verify_response(self.public, self.challenge, oversized))
        with self.assertRaises(ValueError):
            au.decode_response(oversized)

    def test_check_digits_catch_a_mistyped_challenge(self):
        shown = au.format_challenge(self.challenge)
        self.assertEqual(self.challenge, au.parse_challenge(shown))
        with self.assertRaises(ValueError):
            au.parse_challenge(tamper(shown.replace(" ", ""), 5))
        with self.assertRaises(ValueError):
            au.parse_challenge("123")
        with self.assertRaises(ValueError):
            au.parse_challenge("")

    def test_canonical_message_is_length_prefixed(self):
        message = au.canonical_message(self.challenge)
        self.assertEqual(VECTOR_CANONICAL, message.hex())
        # Moving a character between fields must change the message
        self.assertNotEqual(
            au.canonical_message(self.challenge),
            au.canonical_message(self.challenge[:-1]) + b"6",
        )

    def test_response_encoding_round_trips(self):
        signature = self.key.sign(au.canonical_message(self.challenge))
        self.assertEqual(signature, au.decode_response(au.encode_response(signature)))


class VectorTest(unittest.TestCase):
    """The fixed case the app's own test suite checks as well."""

    def test_vector_verifies(self):
        public = au.public_key_from_hex(VECTOR_PUBLIC_KEY)
        self.assertTrue(au.verify_response(public, VECTOR_CHALLENGE, VECTOR_RESPONSE))

    def test_vector_challenge_is_shown_the_same_way(self):
        self.assertEqual(VECTOR_CHALLENGE_SHOWN, au.format_challenge(VECTOR_CHALLENGE))
        self.assertEqual(VECTOR_CHALLENGE, au.parse_challenge(VECTOR_CHALLENGE_SHOWN))

    def test_vector_is_refused_for_another_challenge(self):
        public = au.public_key_from_hex(VECTOR_PUBLIC_KEY)
        self.assertFalse(au.verify_response(public, "1" * 40, VECTOR_RESPONSE))

    def test_public_key_hex_is_rejected_when_it_is_not_a_key(self):
        for broken in ("", "abcd", "z" * 64):
            with self.assertRaises(ValueError):
                au.public_key_from_hex(broken)


class KeyFileTest(unittest.TestCase):
    def test_keygen_writes_a_private_key_only_its_owner_can_read(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nested" / "admin-unlock-key.pem"
            self.assertEqual(0, au.main(["--key-file", str(path), "keygen"]))
            self.assertTrue(path.exists())
            mode = stat.S_IMODE(path.stat().st_mode)
            self.assertEqual(0, mode & (stat.S_IRWXG | stat.S_IRWXO), "%o" % mode)
            # And it loads back into a key that signs what the app accepts
            key = au.load_private_key(path)
            response = au.sign_challenge(key, VECTOR_CHALLENGE)
            self.assertTrue(au.verify_response(key.public_key(), VECTOR_CHALLENGE, response))

    def test_keygen_refuses_to_replace_a_key_without_rotate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "admin-unlock-key.pem"
            self.assertEqual(0, au.main(["--key-file", str(path), "keygen"]))
            before = path.read_bytes()
            self.assertEqual(1, au.main(["--key-file", str(path), "keygen"]))
            self.assertEqual(before, path.read_bytes())

    def test_a_seed_in_the_environment_is_used_when_there_is_one(self):
        key = Ed25519PrivateKey.generate()
        seed = key.private_bytes_raw()
        os.environ["LESSER_EVIL_UNLOCK_KEY"] = __import__("base64").b64encode(seed).decode()
        try:
            loaded = au.load_private_key(Path("/nonexistent/key.pem"))
            self.assertEqual(
                au.public_key_hex(key.public_key()), au.public_key_hex(loaded.public_key())
            )
        finally:
            del os.environ["LESSER_EVIL_UNLOCK_KEY"]


if __name__ == "__main__":
    unittest.main()
