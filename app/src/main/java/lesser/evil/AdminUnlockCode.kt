package lesser.evil

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom

/**
 * The offline unlock code: the lock screen shows a random challenge, the admin
 * signs it with the private key only they hold, and the app verifies that
 * signature against the public key built into it. Nothing leaves the device,
 * and the app carries no way to produce a code itself.
 *
 * Codes are digits only, so they survive being read out over the phone. Keep
 * this in sync with tools/admin-unlock/admin_unlock.py.
 */
object AdminUnlockCode {
    /** Bumped whenever the signed bytes change, so old codes never fit a new format */
    const val ProtocolVersion = "1"
    /** Names this app and this purpose, so a signature for anything else won't fit */
    const val ApplicationId = "lesser.evil.app-unlock"
    /** 40 digits are about 132.9 bits, drawn fresh for every lock screen */
    const val ChallengeDigits = 40
    /** A 64 byte signature never takes more than 155 decimal digits */
    const val ResponseDigits = 155
    const val CheckDigits = 2
    const val ChallengeGroup = 6
    const val ResponseGroup = 5
    private const val SignatureBytes = 64
    private const val PublicKeyBytes = 32
    private val NinetySeven = BigInteger.valueOf(97)

    /**
     * The exact bytes that get signed. Every field carries its own length, so
     * no two different sets of fields can ever produce the same message.
     */
    fun canonicalMessage(challenge: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (field in arrayOf(ProtocolVersion, ApplicationId, challenge)) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            out.write(bytes.size ushr 8)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    /** Two ISO 7064 MOD 97-10 digits, so a mistyped code is caught before verifying */
    fun checkDigits(payload: String): String {
        val remainder = BigInteger(payload + "00").mod(NinetySeven).toInt()
        return "%02d".format((98 - remainder) % 97)
    }

    private fun checkDigitsMatch(digits: String) = BigInteger(digits).mod(NinetySeven).toInt() == 1

    /** A fresh challenge: [ChallengeDigits] digits from a cryptographic generator */
    fun newChallenge(random: SecureRandom = SecureRandom()): String {
        val builder = StringBuilder(ChallengeDigits)
        repeat(ChallengeDigits) { builder.append(random.nextInt(10)) }
        return builder.toString()
    }

    /** Everything that isn't a digit is separator, however the code was typed or pasted */
    fun digitsOnly(text: String) = text.filter { it in '0'..'9' }

    private fun grouped(digits: String, size: Int) =
        digits.chunked(size).joinToString(" ")

    /** The challenge as the lock screen shows it: check digits added, grouped to be read out */
    fun formatChallenge(challenge: String) =
        grouped(challenge + checkDigits(challenge), ChallengeGroup)

    /** The response as the admin tool prints it, for anyone comparing the two sides */
    fun formatResponse(response: String) = grouped(digitsOnly(response), ResponseGroup)

    /** The 32 byte key out of [AdminUnlockPublicKeyHex], or null when none was embedded */
    fun publicKey(hex: String = AdminUnlockPublicKeyHex): ByteArray? {
        val trimmed = hex.trim()
        if (trimmed.length != PublicKeyBytes * 2) return null
        val bytes = ByteArray(PublicKeyBytes)
        for (i in bytes.indices) {
            val high = Character.digit(trimmed[i * 2], 16)
            val low = Character.digit(trimmed[i * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    /** The signature out of a typed response, or null when the digits don't hold one */
    fun decodeResponse(response: String): ByteArray? {
        val digits = digitsOnly(response)
        if (digits.length != ResponseDigits + CheckDigits) return null
        if (!checkDigitsMatch(digits)) return null
        val value = BigInteger(digits.substring(0, ResponseDigits))
        val bytes = value.toByteArray()
        // BigInteger drops leading zeros and may add a sign byte, the signature has neither
        val start = if (bytes.size > SignatureBytes) bytes.size - SignatureBytes else 0
        // Anything dropped here would be a value too large to be a signature
        for (i in 0 until start) if (bytes[i] != 0.toByte()) return null
        val signature = ByteArray(SignatureBytes)
        System.arraycopy(bytes, start, signature, SignatureBytes - (bytes.size - start), bytes.size - start)
        return signature
    }

    /**
     * True when [response] is the admin's signature over [challenge]. The
     * comparison is the Ed25519 verification itself, never a match against a
     * stored code.
     */
    fun verify(challenge: String, response: String, publicKey: ByteArray?): Boolean {
        if (publicKey == null || publicKey.size != PublicKeyBytes) return false
        if (challenge.length != ChallengeDigits || challenge.any { it !in '0'..'9' }) return false
        val signature = decodeResponse(response) ?: return false
        val message = canonicalMessage(challenge)
        return try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * The challenge the lock screen is showing right now. It lives in memory only:
 * every lock screen draws a new one, and a code that unlocked the app is spent
 * the moment it worked.
 */
object AdminUnlockChallenge {
    private val random = SecureRandom()

    /** The key this build was given, standing in for another one only in tests */
    internal var publicKey = AdminUnlockCode.publicKey()

    /** False when this build carries no admin public key, and codes are pointless */
    val enabled: Boolean get() = publicKey != null

    var current: String? = null
        private set

    /** Drops the challenge in hand and draws a new one, whoever saw the old one */
    fun regenerate(): String {
        val challenge = AdminUnlockCode.newChallenge(random)
        current = challenge
        return challenge
    }

    fun invalidate() {
        current = null
    }

    /** The current challenge as the lock screen shows it */
    fun formatted(): String? = current?.let { AdminUnlockCode.formatChallenge(it) }

    /** True when [response] signs the current challenge, which it then spends */
    fun verify(response: String): Boolean {
        val challenge = current ?: return false
        if (!AdminUnlockCode.verify(challenge, response, publicKey)) return false
        invalidate()
        return true
    }
}
