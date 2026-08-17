#!/usr/bin/env python3
"""Admin side of the lesser evil offline unlock challenge-response.

This tool holds the Ed25519 private key and turns a challenge shown by a
locked app into the response that unlocks it. It is not part of the app
package, and it never prints or logs the private key.

    admin_unlock.py keygen          create the key pair (once)
    admin_unlock.py pubkey          print the public key to embed in the app
    admin_unlock.py sign CHALLENGE  turn a challenge into a response
    admin_unlock.py verify CHALLENGE RESPONSE   check a response, public key only

The private key is read from the file named by --key-file, or by the
LESSER_EVIL_UNLOCK_KEY_FILE environment variable, or from a base64 seed in
the LESSER_EVIL_UNLOCK_KEY environment variable. Keep it outside the
repository; the default path already is.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import os
import stat
import sys
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)
from cryptography.hazmat.primitives.serialization import (
    Encoding,
    NoEncryption,
    PrivateFormat,
    PublicFormat,
    load_pem_private_key,
)

# --- Protocol ---------------------------------------------------------------
# Keep in sync with AdminUnlockCode.kt in the app.

PROTOCOL_VERSION = "1"
APPLICATION_ID = "lesser.evil.app-unlock"
CHALLENGE_DIGITS = 40  # ~132.9 bits of randomness
SIGNATURE_BYTES = 64
RESPONSE_DIGITS = 155  # 2^512 - 1 takes 155 decimal digits
CHECK_DIGITS = 2
CHALLENGE_GROUP = 6
RESPONSE_GROUP = 5

DEFAULT_KEY_FILE = Path.home() / ".config" / "lesser-evil" / "admin-unlock-key.pem"


def canonical_message(challenge: str) -> bytes:
    """The exact bytes that get signed.

    Every field is prefixed with its length, so no two different field
    combinations can ever produce the same message.
    """
    fields = (PROTOCOL_VERSION, APPLICATION_ID, challenge)
    out = bytearray()
    for field in fields:
        encoded = field.encode("utf-8")
        out += len(encoded).to_bytes(2, "big")
        out += encoded
    return bytes(out)


def check_digits(payload: str) -> str:
    """Two ISO 7064 MOD 97-10 digits, so a mistyped code is caught early."""
    return "%02d" % ((98 - int(payload + "00") % 97) % 97)


def _digits_only(text: str) -> str:
    return "".join(c for c in text if c.isdigit())


def _grouped(digits: str, size: int) -> str:
    return " ".join(digits[i:i + size] for i in range(0, len(digits), size))


def format_challenge(challenge: str) -> str:
    """The 40 digit challenge as the app shows it: with check digits, grouped."""
    return _grouped(challenge + check_digits(challenge), CHALLENGE_GROUP)


def parse_challenge(text: str) -> str:
    """The 40 digit challenge out of whatever the admin pasted or typed."""
    digits = _digits_only(text)
    if len(digits) != CHALLENGE_DIGITS + CHECK_DIGITS:
        raise ValueError(
            "a challenge has %d digits, got %d" % (CHALLENGE_DIGITS + CHECK_DIGITS, len(digits))
        )
    if int(digits) % 97 != 1:
        raise ValueError("the challenge check digits don't match, it was mistyped")
    return digits[:CHALLENGE_DIGITS]


def encode_response(signature: bytes) -> str:
    """The 64 byte signature as 155 decimal digits plus check digits."""
    if len(signature) != SIGNATURE_BYTES:
        raise ValueError("a signature is %d bytes" % SIGNATURE_BYTES)
    payload = str(int.from_bytes(signature, "big")).zfill(RESPONSE_DIGITS)
    return payload + check_digits(payload)


def decode_response(text: str) -> bytes:
    """The 64 byte signature out of whatever the user typed."""
    digits = _digits_only(text)
    if len(digits) != RESPONSE_DIGITS + CHECK_DIGITS:
        raise ValueError(
            "a response has %d digits, got %d" % (RESPONSE_DIGITS + CHECK_DIGITS, len(digits))
        )
    if int(digits) % 97 != 1:
        raise ValueError("the response check digits don't match, it was mistyped")
    value = int(digits[:RESPONSE_DIGITS])
    if value >= 1 << (SIGNATURE_BYTES * 8):
        raise ValueError("the response is too large to hold a signature")
    return value.to_bytes(SIGNATURE_BYTES, "big")


def sign_challenge(private_key: Ed25519PrivateKey, challenge: str) -> str:
    return encode_response(private_key.sign(canonical_message(challenge)))


def verify_response(public_key: Ed25519PublicKey, challenge: str, response: str) -> bool:
    try:
        signature = decode_response(response)
    except ValueError:
        return False
    try:
        public_key.verify(signature, canonical_message(challenge))
    except InvalidSignature:
        return False
    return True


def public_key_hex(public_key: Ed25519PublicKey) -> str:
    """The 32 byte public key as 64 hex characters, the way the app embeds it."""
    return public_key.public_bytes(Encoding.Raw, PublicFormat.Raw).hex()


def public_key_from_hex(encoded: str) -> Ed25519PublicKey:
    try:
        raw = bytes.fromhex(encoded.strip())
    except ValueError as e:
        raise ValueError("the public key is not valid hex") from e
    if len(raw) != 32:
        raise ValueError("an Ed25519 public key is 32 bytes, 64 hex characters")
    return Ed25519PublicKey.from_public_bytes(raw)


# --- Key handling -----------------------------------------------------------


def load_private_key(key_file: Path | None) -> Ed25519PrivateKey:
    seed = os.environ.get("LESSER_EVIL_UNLOCK_KEY")
    if seed:
        try:
            raw = base64.b64decode(seed, validate=True)
        except binascii.Error as e:
            raise SystemExit("LESSER_EVIL_UNLOCK_KEY is not valid base64") from e
        if len(raw) != 32:
            raise SystemExit("LESSER_EVIL_UNLOCK_KEY must hold a 32 byte seed")
        return Ed25519PrivateKey.from_private_bytes(raw)
    path = resolve_key_file(key_file)
    if not path.exists():
        raise SystemExit(
            "no private key at %s\nRun 'admin_unlock.py keygen' first, or point "
            "--key-file at your key." % path
        )
    key = load_pem_private_key(path.read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise SystemExit("%s does not hold an Ed25519 private key" % path)
    return key


def resolve_key_file(key_file: Path | None) -> Path:
    if key_file is not None:
        return key_file
    from_env = os.environ.get("LESSER_EVIL_UNLOCK_KEY_FILE")
    return Path(from_env) if from_env else DEFAULT_KEY_FILE


def write_private_key(private_key: Ed25519PrivateKey, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pem = private_key.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption())
    # Create it unreadable to anybody else before the bytes ever land in it
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(fd, "wb") as f:
        f.write(pem)


# --- Commands ---------------------------------------------------------------


def cmd_keygen(args: argparse.Namespace) -> int:
    path = resolve_key_file(args.key_file)
    if path.exists():
        print("A private key already exists at %s" % path, file=sys.stderr)
        print(
            "\nWARNING: replacing it changes the public key. Every app build that\n"
            "carries the current public key will stop accepting your responses,\n"
            "and a device locked with the old key can only be unlocked by\n"
            "installing a build that carries the new public key.\n",
            file=sys.stderr,
        )
        if not args.rotate:
            print("Re-run with --rotate to replace it.", file=sys.stderr)
            return 1
        answer = input("Type 'replace' to confirm: ").strip()
        if answer != "replace":
            print("Cancelled, the key was left alone.", file=sys.stderr)
            return 1
    private_key = Ed25519PrivateKey.generate()
    write_private_key(private_key, path)
    print("Private key written to %s (keep it there, out of the repository)" % path, file=sys.stderr)
    print("Embed this public key in the app, in AdminUnlockKey.kt:")
    print(public_key_hex(private_key.public_key()))
    return 0


def cmd_pubkey(args: argparse.Namespace) -> int:
    private_key = load_private_key(args.key_file)
    print(public_key_hex(private_key.public_key()))
    return 0


def cmd_sign(args: argparse.Namespace) -> int:
    challenge = parse_challenge(args.challenge)
    private_key = load_private_key(args.key_file)
    response = sign_challenge(private_key, challenge)
    print("Challenge: %s" % format_challenge(challenge), file=sys.stderr)
    print("Response, read it out in groups of %d:\n" % RESPONSE_GROUP, file=sys.stderr)
    print(_grouped(response, RESPONSE_GROUP))
    return 0


def cmd_verify(args: argparse.Namespace) -> int:
    challenge = parse_challenge(args.challenge)
    if args.public_key:
        public_key = public_key_from_hex(args.public_key)
    else:
        public_key = load_private_key(args.key_file).public_key()
    if verify_response(public_key, challenge, args.response):
        print("The response matches the challenge.")
        return 0
    print("The response does not match the challenge.", file=sys.stderr)
    return 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="admin_unlock.py", description="Offline unlock codes for lesser evil"
    )
    parser.add_argument(
        "--key-file", type=Path, default=None,
        help="private key file (default: $LESSER_EVIL_UNLOCK_KEY_FILE or %s)" % DEFAULT_KEY_FILE
    )
    sub = parser.add_subparsers(dest="command", required=True)

    keygen = sub.add_parser("keygen", help="create the key pair")
    keygen.add_argument("--rotate", action="store_true", help="replace an existing key")
    keygen.set_defaults(func=cmd_keygen)

    pubkey = sub.add_parser("pubkey", help="print the public key to embed in the app")
    pubkey.set_defaults(func=cmd_pubkey)

    sign = sub.add_parser("sign", help="turn a challenge into a response")
    sign.add_argument("challenge", help="the digits the locked app shows")
    sign.set_defaults(func=cmd_sign)

    verify = sub.add_parser("verify", help="check a response the way the app does")
    verify.add_argument("challenge")
    verify.add_argument("response")
    verify.add_argument("--public-key", help="hex public key, instead of the private key file")
    verify.set_defaults(func=cmd_verify)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except ValueError as e:
        print("%s" % e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
