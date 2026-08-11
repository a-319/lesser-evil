package lesser.evil

/**
 * The Ed25519 public key of whoever hands out unlock codes for this build, as
 * 64 hex characters. Only the public key belongs here: the private key stays
 * with the admin, and without it nobody can produce a code this build accepts.
 *
 * Empty means no admin was set up, and the unlock code is hidden from the lock
 * screen. Create a key pair with `tools/admin-unlock/admin_unlock.py keygen`
 * and paste the public key it prints between the quotes.
 */
const val AdminUnlockPublicKeyHex = ""
