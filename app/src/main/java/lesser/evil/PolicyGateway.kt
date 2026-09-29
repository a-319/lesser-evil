package lesser.evil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A blocking function whose blocks carry an owner. */
enum class BlockKind { Hidden, Suspended, UninstallBlocked, Ucd, Mdd, UserRestriction, DeviceState }

/**
 * A device-wide state that is a block like any other: it has no package or restriction to key it
 * by, so it is keyed by name instead. Turning one of these on used to be untracked, which made it
 * the one way left to undo what the admin had set - a child could re-enable a camera the parent
 * had disabled, from the app or from the launcher shortcut. Owned like everything else, the same
 * rule applies: whoever turned it on may turn it off, and nobody else.
 *
 * [key] is stored, so it may not change; the name of the entry may.
 */
enum class DeviceState(val key: String) {
    Camera("camera"),
    ScreenCapture("screen_capture"),
    StatusBar("status_bar"),
    MasterVolume("master_volume");

    companion object {
        /** Null for a key this build does not know, which is left alone rather than guessed at. */
        fun of(key: String): DeviceState? = entries.find { it.key == key }
    }
}

/** Who made a change. Every change to a tracked block has to name one. */
@Serializable
sealed interface Actor {
    /** The admin profile. Nothing is withheld from it. */
    @Serializable @SerialName("admin")
    object Admin : Actor

    /** One child profile. Profiles are told apart by [profileId]. */
    @Serializable @SerialName("child")
    data class Child(val profileId: Int = 0) : Actor

    /**
     * Something acting on its own rather than a person: a mode switch, a scheduled limit, the
     * admin API, a launcher shortcut. [source] identifies which, so two of them cannot take each
     * other's blocks.
     */
    @Serializable @SerialName("automation")
    data class Automation(val source: String) : Actor {
        companion object {
            fun modeSwitch(id: Int) = Automation("switch:$id")
            val Api = Automation("api")
            val Shortcut = Automation("shortcut")
        }
    }
}

/** When a block may be lifted, which is not the same question as who created it. */
@Serializable
sealed interface ReleaseRule {
    /** Whoever created it may lift it again. The ordinary case. */
    @Serializable @SerialName("by_owner")
    object ByOwner : ReleaseRule

    /** Only the admin may lift it, whoever created it. */
    @Serializable @SerialName("admin_only")
    object AdminOnly : ReleaseRule

    /**
     * Nobody may lift it before [millis], not even the profile that asked for it. This is what
     * makes a self-imposed lockout binding on the person who set it.
     */
    @Serializable @SerialName("not_before")
    data class NotBefore(val millis: Long) : ReleaseRule

    /** Only the automation that created it may lift it, by doing whatever it does. */
    @Serializable @SerialName("automatic")
    object Automatic : ReleaseRule
}

@Serializable
data class BlockRecord(val owner: Actor, val release: ReleaseRule = ReleaseRule.ByOwner)

/**
 * The one place a tracked block changes.
 *
 * Ownership used to be recorded by each caller after it had already changed the policy, which
 * meant every new path had to remember to do it - and each one that forgot became a way for a
 * profile to take over a block that was not its own. Here a change is not possible without
 * naming an [Actor], so the record is written because the change happened rather than alongside
 * it, and the permission question is answered in one place instead of at every call site.
 *
 * The one sanctioned exception is lock task mode, which lifts an app's hidden or suspended state
 * for the length of a session and puts it back on exit. That is a pause in enforcement rather
 * than a change of owner, so it deliberately leaves records alone - and the gateway refuses
 * everyone but the admin while the lift is in place, so nothing can be claimed through the gap.
 */
object PolicyGateway {
    /** Why a change was refused. */
    sealed interface Denial {
        /** The block belongs to someone else, so it cannot be lifted or taken over. */
        data class OwnedBy(val owner: Actor) : Denial
        /** Only the admin may lift this one. */
        object AdminOnly : Denial
        /** Held by an automation, which has to release it itself. */
        object HeldByAutomation : Denial
        /** Asked for until [millis]; not even the profile that asked may end it early. */
        data class LockedUntil(val millis: Long) : Denial
        /** Lock task mode has this app lifted for the current session. */
        object TemporarilyLifted : Denial
        /** The system did not carry the change out. */
        object Failed : Denial
    }

    /** Why [actor] may not set [key] to [blocked], or null if it may. */
    fun denialFor(actor: Actor, kind: BlockKind, key: String, blocked: Boolean): Denial? {
        if (actor is Actor.Admin) return null
        if (LockTaskUtils.isLifted(kind, key)) return Denial.TemporarilyLifted
        if (blanketMeteredDataHeld(kind)) return Denial.HeldByAutomation
        val record = BlockOwnership.recordFor(kind, key)
        if (blocked) {
            // A block that already has an owner cannot be taken over by making it again, which
            // is what let a profile claim an admin block that had been lifted for a moment
            if (record == null || record.owner == actor) return null
            return notMine(record.owner)
        }
        // Nothing recorded means nobody tracked created it, so it predates the record and is the
        // admin's; that is the safe reading, since guessing the other way hands a block away
        if (record == null) return Denial.OwnedBy(Actor.Admin)
        if (record.owner != actor) return notMine(record.owner)
        // The owner is asking. Only a rule that binds the owner itself can still refuse
        return when (val rule = record.release) {
            is ReleaseRule.ByOwner, is ReleaseRule.Automatic -> null
            is ReleaseRule.AdminOnly -> Denial.AdminOnly
            is ReleaseRule.NotBefore ->
                if (System.currentTimeMillis() >= rule.millis) null
                else Denial.LockedUntil(rule.millis)
        }
    }

    /**
     * Whether [actor] cannot change [key] away from its current state [blocked]. A screen asks
     * this before it offers the control at all, so a function someone else turned on is visibly
     * not this profile's rather than a refusal after the tap. It asks the very [denialFor] the
     * change itself would go through, so what is greyed out and what is refused cannot drift.
     */
    fun lockedFor(actor: Actor, kind: BlockKind, key: String, blocked: Boolean): Boolean =
        denialFor(actor, kind, key, !blocked) != null

    /**
     * Whether no key of [kind] is [actor]'s to touch, whatever it is. A blanket policy owns the
     * whole list rather than single entries, so there is nothing to add to it either.
     */
    fun kindLockedFor(actor: Actor, kind: BlockKind): Boolean =
        actor !is Actor.Admin && blanketMeteredDataHeld(kind)

    /** Why [owner]'s block is not this actor's to change, worded for whoever holds it */
    private fun notMine(owner: Actor): Denial =
        if (owner is Actor.Automation) Denial.HeldByAutomation else Denial.OwnedBy(owner)

    /**
     * Sets [key] to [blocked] on behalf of [actor], recording who owns the result.
     * @return null when the change was made, otherwise why it was not.
     */
    fun setBlock(
        actor: Actor, kind: BlockKind, key: String, blocked: Boolean,
        release: ReleaseRule = ReleaseRule.ByOwner
    ): Denial? {
        denialFor(actor, kind, key, blocked)?.let { return it }
        apply(kind, listOf(key), blocked)
        // Confirm against the device: setPackagesSuspended reports what it could not do in its
        // result rather than throwing, and a change that did not happen must not be recorded
        if (BlockOwnership.isBlocked(kind, key) != blocked) return Denial.Failed
        if (blocked) BlockOwnership.put(kind, key, BlockRecord(actor, release))
        else BlockOwnership.remove(kind, key)
        return null
    }

    /**
     * The list form, which changes the keys [actor] is allowed to change and leaves the rest.
     * @return the keys that were refused, with the reason for each.
     */
    fun setBlocks(
        actor: Actor, kind: BlockKind, keys: List<String>, blocked: Boolean,
        release: ReleaseRule = ReleaseRule.ByOwner
    ): Map<String, Denial> {
        val refused = mutableMapOf<String, Denial>()
        val allowed = keys.filter { key ->
            val denial = denialFor(actor, kind, key, blocked)
            if (denial != null) refused[key] = denial
            denial == null
        }
        if (allowed.isEmpty()) return refused
        apply(kind, allowed, blocked)
        allowed.forEach { key ->
            if (BlockOwnership.isBlocked(kind, key) != blocked) {
                refused[key] = Denial.Failed
            } else if (blocked) {
                BlockOwnership.put(kind, key, BlockRecord(actor, release))
            } else {
                BlockOwnership.remove(kind, key)
            }
        }
        return refused
    }

    /**
     * Makes the blocked set of [kind] exactly [keys], for the two kinds the system offers no other
     * way to change - the list is handed over whole, and what that means is a difference.
     *
     * The difference is taken against the device rather than against what the caller believes, so
     * a key that is already blocked by someone else is not in it and is left alone. Each side of
     * it is then an ordinary change that the owner of each key may or may not be allowed to make,
     * which is why this can be offered to a profile at all.
     *
     * @return the keys that were refused, with the reason for each.
     */
    fun setWholeList(actor: Actor, kind: BlockKind, keys: List<String>): Map<String, Denial> {
        val current = currentList(kind) ?: return keys.associateWith { Denial.Failed }
        val wanted = keys.distinct()
        val refused = mutableMapOf<String, Denial>()
        refused += setBlocks(actor, kind, wanted - current.toSet(), true)
        refused += setBlocks(actor, kind, current - wanted.toSet(), false)
        return refused
    }

    /** What [kind] holds right now, for the kinds that are read and written as one list. */
    private fun currentList(kind: BlockKind): List<String>? {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        return try {
            when (kind) {
                BlockKind.Ucd ->
                    if (android.os.Build.VERSION.SDK_INT >= 30)
                        dpm.getUserControlDisabledPackages(dar).distinct() else null
                BlockKind.Mdd ->
                    if (android.os.Build.VERSION.SDK_INT >= 28)
                        dpm.getMeteredDataDisabledPackages(dar).distinct() else null
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * A blanket metered data policy rewrites the whole disabled list rather than one entry, so
     * while a switch carries one, every package's metered data state is the switch's to decide.
     */
    private fun blanketMeteredDataHeld(kind: BlockKind): Boolean =
        kind == BlockKind.Mdd && BlockOwnership.blanketMeteredDataInUse()

    private fun apply(kind: BlockKind, keys: List<String>, blocked: Boolean) {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        when (kind) {
            BlockKind.Hidden -> keys.forEach { dpm.setApplicationHidden(dar, it, blocked) }
            BlockKind.Suspended -> if (android.os.Build.VERSION.SDK_INT >= 24) {
                dpm.setPackagesSuspended(dar, keys.toTypedArray(), blocked)
            }
            BlockKind.UninstallBlocked -> keys.forEach { dpm.setUninstallBlocked(dar, it, blocked) }
            BlockKind.Ucd -> if (android.os.Build.VERSION.SDK_INT >= 30) {
                val current = dpm.getUserControlDisabledPackages(dar)
                dpm.setUserControlDisabledPackages(
                    dar, if (blocked) (current + keys).distinct() else current - keys.toSet()
                )
            }
            BlockKind.Mdd -> if (android.os.Build.VERSION.SDK_INT >= 28) {
                val current = dpm.getMeteredDataDisabledPackages(dar)
                dpm.setMeteredDataDisabledPackages(
                    dar, if (blocked) (current + keys).distinct() else current - keys.toSet()
                )
            }
            BlockKind.UserRestriction -> keys.forEach {
                if (blocked) dpm.addUserRestriction(dar, it) else dpm.clearUserRestriction(dar, it)
            }
            BlockKind.DeviceState -> keys.forEach { key ->
                when (DeviceState.of(key)) {
                    DeviceState.Camera -> dpm.setCameraDisabled(dar, blocked)
                    DeviceState.ScreenCapture -> dpm.setScreenCaptureDisabled(dar, blocked)
                    DeviceState.StatusBar -> dpm.setStatusBarDisabled(dar, blocked)
                    DeviceState.MasterVolume -> dpm.setMasterVolumeMuted(dar, blocked)
                    null -> Unit
                }
            }
        }
    }
}
