package lesser.evil

import android.os.Build.VERSION

/**
 * The blocking functions the user profile can take ownership of. For each one the set of blocks
 * the user profile created is persisted, so ownership survives admin sessions and app restarts.
 * Anything blocked but not user-owned belongs to the admin.
 */
enum class BlockKind { Hidden, Suspended, UninstallBlocked, Ucd, Mdd, UserRestriction }

object BlockOwnership {
    fun get(kind: BlockKind): Set<String> = when (kind) {
        BlockKind.Hidden -> SP.userOwnedHidden
        BlockKind.Suspended -> SP.userOwnedSuspended
        BlockKind.UninstallBlocked -> SP.userOwnedUninstallBlocked
        BlockKind.Ucd -> SP.userOwnedUcd
        BlockKind.Mdd -> SP.userOwnedMdd
        BlockKind.UserRestriction -> SP.userOwnedRestrictions
    }?.split('\n')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun put(kind: BlockKind, value: Set<String>) {
        val text = value.joinToString("\n")
        when (kind) {
            BlockKind.Hidden -> SP.userOwnedHidden = text
            BlockKind.Suspended -> SP.userOwnedSuspended = text
            BlockKind.UninstallBlocked -> SP.userOwnedUninstallBlocked = text
            BlockKind.Ucd -> SP.userOwnedUcd = text
            BlockKind.Mdd -> SP.userOwnedMdd = text
            BlockKind.UserRestriction -> SP.userOwnedRestrictions = text
        }
    }

    /**
     * Records who owns the blocks on [keys] after they were just set to [blocked].
     * A block the user profile creates becomes user-owned; anything the admin sets or either side
     * clears is no longer user-owned - so an admin block always overrides a user one.
     *
     * @param byUserProfile whether the change came from the user profile. Changes from outside a
     * session, such as a launcher shortcut, are not the user profile's and never claim ownership.
     */
    fun record(kind: BlockKind, keys: List<String>, blocked: Boolean, byUserProfile: Boolean) {
        if (keys.isEmpty()) return
        val current = get(kind)
        val updated = if (blocked && byUserProfile) current + keys else current - keys.toSet()
        if (updated != current) put(kind, updated)
    }

    /** Whether [key] is blocked right now, used to tell an existing block from a new one */
    fun isBlocked(kind: BlockKind, key: String): Boolean = try {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        when (kind) {
            BlockKind.Hidden -> dpm.isApplicationHidden(dar, key)
            BlockKind.Suspended -> VERSION.SDK_INT >= 24 && dpm.isPackageSuspended(dar, key)
            BlockKind.UninstallBlocked -> dpm.isUninstallBlocked(dar, key)
            BlockKind.Ucd -> VERSION.SDK_INT >= 30 && key in dpm.getUserControlDisabledPackages(dar)
            BlockKind.Mdd -> VERSION.SDK_INT >= 28 && key in dpm.getMeteredDataDisabledPackages(dar)
            BlockKind.UserRestriction ->
                VERSION.SDK_INT >= 24 && dpm.getUserRestrictions(dar).getBoolean(key)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
