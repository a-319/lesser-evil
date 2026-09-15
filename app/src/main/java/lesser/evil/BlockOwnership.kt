package lesser.evil

import android.os.Build.VERSION
import kotlinx.serialization.json.Json

/**
 * Where the owner of every tracked block is kept. Written only by [PolicyGateway], so a record
 * cannot fall out of step with a change that was made without one.
 */
object BlockOwnership {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: MutableMap<String, MutableMap<String, BlockRecord>>? = null

    private fun records(): MutableMap<String, MutableMap<String, BlockRecord>> {
        cache?.let { return it }
        val stored = SP.blockRecords
        val loaded: MutableMap<String, MutableMap<String, BlockRecord>> = if (stored == null) {
            migrateFromUserOwnedSets()
        } else try {
            json.decodeFromString<Map<String, Map<String, BlockRecord>>>(stored)
                .mapValues { it.value.toMutableMap() }.toMutableMap()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableMapOf()
        }
        cache = loaded
        return loaded
    }

    /**
     * Carries over the flat "owned by the user profile" sets an earlier version kept, so blocks a
     * child already created stay theirs across the upgrade instead of silently becoming the
     * admin's. Those entries were all removable by whoever made them, hence [ReleaseRule.ByOwner].
     */
    private fun migrateFromUserOwnedSets(): MutableMap<String, MutableMap<String, BlockRecord>> {
        val legacy = mapOf(
            BlockKind.Hidden to SP.userOwnedHidden,
            BlockKind.Suspended to SP.userOwnedSuspended,
            BlockKind.UninstallBlocked to SP.userOwnedUninstallBlocked,
            BlockKind.Ucd to SP.userOwnedUcd,
            BlockKind.Mdd to SP.userOwnedMdd,
            BlockKind.UserRestriction to SP.userOwnedRestrictions
        )
        val migrated = mutableMapOf<String, MutableMap<String, BlockRecord>>()
        legacy.forEach { (kind, text) ->
            val keys = text?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
            if (keys.isNotEmpty()) {
                migrated[kind.name] = keys.associateWith {
                    BlockRecord(Actor.Child(), ReleaseRule.ByOwner)
                }.toMutableMap()
            }
        }
        SP.userOwnedHidden = null
        SP.userOwnedSuspended = null
        SP.userOwnedUninstallBlocked = null
        SP.userOwnedUcd = null
        SP.userOwnedMdd = null
        SP.userOwnedRestrictions = null
        if (migrated.isNotEmpty()) save(migrated)
        return migrated
    }

    private fun save(value: Map<String, Map<String, BlockRecord>>) {
        SP.blockRecords = json.encodeToString(value)
    }

    fun recordFor(kind: BlockKind, key: String): BlockRecord? = records()[kind.name]?.get(key)

    /** Every key of [kind] that [actor] owns, for showing a profile what is its own. */
    fun ownedBy(actor: Actor, kind: BlockKind): Set<String> =
        records()[kind.name].orEmpty().filterValues { it.owner == actor }.keys

    internal fun put(kind: BlockKind, key: String, record: BlockRecord) {
        val all = records()
        all.getOrPut(kind.name) { mutableMapOf() }[key] = record
        save(all)
    }

    internal fun remove(kind: BlockKind, key: String) {
        val all = records()
        val forKind = all[kind.name] ?: return
        if (forKind.remove(key) == null) return
        if (forKind.isEmpty()) all.remove(kind.name)
        save(all)
    }

    /** Drops every record an automation owns, used when that automation is taken away. */
    fun releaseAutomation(source: String) {
        val all = records()
        var changed = false
        all.values.forEach { forKind ->
            val owner = Actor.Automation(source)
            val gone = forKind.filterValues { it.owner == owner }.keys
            if (gone.isNotEmpty()) {
                gone.forEach { forKind.remove(it) }
                changed = true
            }
        }
        if (changed) {
            all.entries.removeAll { it.value.isEmpty() }
            save(all)
        }
    }

    /**
     * Whether any mode switch carries a blanket metered data policy. Such a policy owns the whole
     * disabled list rather than single entries, which the gateway has to know before it lets a
     * profile edit any of it.
     */
    var blanketMeteredDataLookup: () -> Boolean = { false }
    fun blanketMeteredDataInUse(): Boolean = try {
        blanketMeteredDataLookup()
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /** Whether [key] is blocked right now, used to confirm a change really took */
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
