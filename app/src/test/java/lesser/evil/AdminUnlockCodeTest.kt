package lesser.evil

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

/**
 * The unlock code as the app sees it. The admin keys here are made up on the
 * spot: no private key belongs in this repository, and none is needed to test
 * the side that only verifies.
 */
class AdminUnlockCodeTest {
    private val random = SecureRandom()

    private fun keyPair(): Pair<Ed25519PrivateKeyParameters, ByteArray> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        val pair = generator.generateKeyPair()
        val private = pair.private as Ed25519PrivateKeyParameters
        return private to private.generatePublicKey().encoded
    }

    /** The admin tool's job, done here with the same protocol the app expects */
    private fun sign(key: Ed25519PrivateKeyParameters, challenge: String): String {
        val message = AdminUnlockCode.canonicalMessage(challenge)
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(message, 0, message.size)
        val payload = BigInteger(1, signer.generateSignature()).toString()
            .padStart(AdminUnlockCode.ResponseDigits, '0')
        return payload + AdminUnlockCode.checkDigits(payload)
    }

    /** Runs [block] against a build that carries [key], then puts the real one back */
    private fun withPublicKey(key: ByteArray?, block: () -> Unit) {
        val embedded = AdminUnlockChallenge.publicKey
        AdminUnlockChallenge.publicKey = key
        try {
            block()
        } finally {
            AdminUnlockChallenge.publicKey = embedded
            AdminUnlockChallenge.invalidate()
        }
    }

    private fun tamper(digits: String, index: Int): String {
        val replacement = if (digits[index] == '7') '3' else '7'
        return digits.substring(0, index) + replacement + digits.substring(index + 1)
    }

    @Test
    fun `signature over the current challenge unlocks`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        assertTrue(AdminUnlockCode.verify(challenge, sign(private, challenge), public))
    }

    @Test
    fun `a response may be typed with any separators`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val response = AdminUnlockCode.formatResponse(sign(private, challenge))
        assertTrue(response.contains(' '))
        assertTrue(AdminUnlockCode.verify(challenge, response, public))
        assertTrue(AdminUnlockCode.verify(challenge, response.replace(" ", "-"), public))
    }

    @Test
    fun `signature over another challenge does not unlock`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val other = AdminUnlockCode.newChallenge(random)
        assertNotEquals(challenge, other)
        assertFalse(AdminUnlockCode.verify(challenge, sign(private, other), public))
    }

    @Test
    fun `a changed digit in the challenge does not unlock`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val response = sign(private, challenge)
        for (index in intArrayOf(0, 17, AdminUnlockCode.ChallengeDigits - 1)) {
            assertFalse(AdminUnlockCode.verify(tamper(challenge, index), response, public))
        }
    }

    @Test
    fun `a changed digit in the response does not unlock`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val response = sign(private, challenge)
        for (index in intArrayOf(0, 80, AdminUnlockCode.ResponseDigits - 1)) {
            val tampered = tamper(response, index)
            // The check digits catch it before the signature does, both are a refusal
            assertFalse(AdminUnlockCode.verify(challenge, tampered, public))
            val payload = tampered.substring(0, AdminUnlockCode.ResponseDigits)
            val repaired = payload + AdminUnlockCode.checkDigits(payload)
            assertFalse(AdminUnlockCode.verify(challenge, repaired, public))
        }
    }

    @Test
    fun `a changed check digit does not unlock`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val response = sign(private, challenge)
        assertFalse(AdminUnlockCode.verify(challenge, tamper(response, response.length - 1), public))
    }

    @Test
    fun `signature from another private key does not unlock`() {
        val (_, public) = keyPair()
        val (otherPrivate, _) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        assertFalse(AdminUnlockCode.verify(challenge, sign(otherPrivate, challenge), public))
    }

    @Test
    fun `damaged or missing input does not unlock`() {
        val (private, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        val response = sign(private, challenge)
        assertFalse(AdminUnlockCode.verify(challenge, "", public))
        assertFalse(AdminUnlockCode.verify(challenge, "   ", public))
        assertFalse(AdminUnlockCode.verify(challenge, "not digits at all", public))
        assertFalse(AdminUnlockCode.verify(challenge, response.dropLast(1), public))
        assertFalse(AdminUnlockCode.verify(challenge, response + "0", public))
        assertFalse(AdminUnlockCode.verify(challenge, "9".repeat(response.length), public))
        assertFalse(AdminUnlockCode.verify("", response, public))
        assertFalse(AdminUnlockCode.verify(challenge.dropLast(1), response, public))
        assertFalse(AdminUnlockCode.verify(challenge, response, null))
        assertFalse(AdminUnlockCode.verify(challenge, response, ByteArray(31)))
        assertFalse(AdminUnlockCode.verify(challenge, response, ByteArray(32)))
    }

    @Test
    fun `a response too large to be a signature does not unlock`() {
        val (_, public) = keyPair()
        val challenge = AdminUnlockCode.newChallenge(random)
        // 155 nines is above 2^512, so it holds no signature at all
        val payload = "9".repeat(AdminUnlockCode.ResponseDigits)
        assertNull(AdminUnlockCode.decodeResponse(payload + AdminUnlockCode.checkDigits(payload)))
        assertFalse(AdminUnlockCode.verify(challenge, payload + AdminUnlockCode.checkDigits(payload), public))
    }

    @Test
    fun `the previous challenge is dropped when a new one is drawn`() {
        val (private, public) = keyPair()
        withPublicKey(public) {
            val first = AdminUnlockChallenge.regenerate()
            val response = sign(private, first)
            val second = AdminUnlockChallenge.regenerate()
            assertNotEquals(first, second)
            assertEquals(second, AdminUnlockChallenge.current)
            // The code the admin gave out for the challenge that is gone is worthless
            assertFalse(AdminUnlockChallenge.verify(response))
            AdminUnlockChallenge.invalidate()
            assertNull(AdminUnlockChallenge.current)
            assertFalse(AdminUnlockChallenge.verify(sign(private, second)))
        }
    }

    @Test
    fun `a code unlocks once and is then spent`() {
        val (private, public) = keyPair()
        withPublicKey(public) {
            val challenge = AdminUnlockChallenge.regenerate()
            val response = sign(private, challenge)
            assertTrue(AdminUnlockChallenge.verify(response))
            assertNull(AdminUnlockChallenge.current)
            assertFalse(AdminUnlockChallenge.verify(response))
        }
    }

    @Test
    fun `codes are hidden when no admin key was embedded`() {
        withPublicKey(null) { assertFalse(AdminUnlockChallenge.enabled) }
        withPublicKey(AdminUnlockCode.publicKey(TestPublicKeyHex)) {
            assertTrue(AdminUnlockChallenge.enabled)
        }
    }

    @Test
    fun `challenges keep their distance`() {
        val seen = HashSet<String>()
        repeat(500) {
            val challenge = AdminUnlockCode.newChallenge(random)
            assertEquals(AdminUnlockCode.ChallengeDigits, challenge.length)
            assertTrue(challenge.all { it in '0'..'9' })
            assertTrue(seen.add(challenge))
        }
    }

    @Test
    fun `check digits catch a mistyped challenge`() {
        val challenge = AdminUnlockCode.newChallenge(random)
        val shown = AdminUnlockCode.digitsOnly(AdminUnlockCode.formatChallenge(challenge))
        assertEquals(AdminUnlockCode.ChallengeDigits + AdminUnlockCode.CheckDigits, shown.length)
        assertEquals(1, BigInteger(shown).mod(BigInteger.valueOf(97)).toInt())
        assertNotEquals(1, BigInteger(tamper(shown, 5)).mod(BigInteger.valueOf(97)).toInt())
    }

    @Test
    fun `an embedded key is only read when it is a key`() {
        assertNull(AdminUnlockCode.publicKey(""))
        assertNull(AdminUnlockCode.publicKey("abcd"))
        assertNull(AdminUnlockCode.publicKey("z".repeat(64)))
        assertEquals(32, AdminUnlockCode.publicKey(TestPublicKeyHex)?.size)
    }

    /**
     * The fixed case below was produced by tools/admin-unlock/admin_unlock.py,
     * and the same three values are checked in its own test suite. If either
     * side ever changes what it signs or how it encodes, this fails.
     */
    @Test
    fun `the admin tool and the app agree`() {
        val public = AdminUnlockCode.publicKey(TestPublicKeyHex)
        assertTrue(AdminUnlockCode.verify(TestChallenge, TestResponse, public))
        assertArrayEquals(
            TestCanonicalMessageHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            AdminUnlockCode.canonicalMessage(TestChallenge)
        )
        assertEquals(TestChallengeShown, AdminUnlockCode.formatChallenge(TestChallenge))
    }

    companion object {
        /** A throwaway key pair, whose private half was dropped once it signed this */
        const val TestPublicKeyHex = "8aa40c022b9d23c94747a42954232b388d61f2d0cc81c3c7c2354c03c218544b"
        const val TestChallenge = "3074185296307418529630741852963074185296"
        const val TestChallengeShown = "307418 529630 741852 963074 185296 307418 529646"
        const val TestResponse = "121956202566744964445682564286215717246545329279185402760066558190694145581" +
            "8552841252729217344427282614312072316000203406100070332131898703974576235009511387"
        const val TestCanonicalMessageHex = "00013100166c65737365722e6576696c2e6170702d756e6c6f636b0028" +
            "33303734313835323936333037343138353239363330373431383532393633303734313835323936"
    }
}
