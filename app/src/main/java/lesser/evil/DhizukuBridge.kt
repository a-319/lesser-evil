package lesser.evil

import android.os.Parcel
import lesser.evil.dpm.isValidPackageName

/**
 * Turns a Dhizuku client's raw call on the device policy service into an operation this app can
 * put its own rules to, so a grant can carry an [Actor] instead of handing over the whole device.
 *
 * A client written for Dhizuku talks to the system directly: it marshals a call for
 * IDevicePolicyManager and asks the server to make it with the server's identity. Nothing in that
 * protocol says which function is being asked for, which is why a grant used to be all or nothing.
 * Sitting in the middle of it, this decides what the call actually is and hands the ones it
 * recognises to [PolicyGateway] under the granting profile's name - the same path, the same
 * ownership and the same refusals as the button in the app.
 *
 * Everything here is built to fail closed. A call is refused unless its function is one of the few
 * listed below, its arguments parse against a shape that consumes the payload exactly, and every
 * shape that parses agrees on the answer. A function whose signature shifted in a way this does
 * not expect therefore stops working rather than doing something else, and an admin grant never
 * comes through here at all.
 */
object DhizukuBridge {
    /** The only service a profile's grant may be about. */
    const val DPM_DESCRIPTOR = "android.app.admin.IDevicePolicyManager"

    /** How the answer is shaped, so the client's own proxy can still read what it expects. */
    enum class Reply { Void, Bool, StringArray }

    /** A client's call, once it is known what it asks for. */
    sealed interface Call {
        /** A tracked block to run through the gateway as the client's actor. */
        data class Block(
            val kind: BlockKind, val keys: List<String>, val blocked: Boolean, val reply: Reply
        ) : Call
        /** Changes nothing, so it may go on to the system unaltered. */
        object PassThrough : Call
        /** Not recognised, or not a profile's to ask for. */
        object Refused : Call
    }

    /** How a function's arguments carry what it acts on. */
    private enum class Args {
        /** One package and a flag, as setApplicationHidden does */
        PackageFlag,
        /** A list of packages and a flag, as setPackagesSuspended does */
        PackagesFlag,
        /** One key, with the function's name saying which way it goes */
        Key,
        /** One key and a flag */
        KeyFlag,
        /** A flag alone, for a state whose key is the function itself */
        Flag
    }

    private class Op(
        val kind: BlockKind,
        val args: Args,
        val reply: Reply,
        /** Set when the name itself says which way it goes, as add and clear do */
        val blocked: Boolean? = null,
        /** Set for a device-wide state, which has no key in the call */
        val key: String? = null
    )

    /**
     * The functions a profile's grant may change something with. Names rather than transaction
     * codes: a code is an ordinal that moves between Android versions, a name does not.
     *
     * Both spellings of the restriction call are listed because the framework has used each.
     */
    private val writes = mapOf(
        "setApplicationHidden" to Op(BlockKind.Hidden, Args.PackageFlag, Reply.Bool),
        "setUninstallBlocked" to Op(BlockKind.UninstallBlocked, Args.PackageFlag, Reply.Void),
        "setPackagesSuspended" to Op(BlockKind.Suspended, Args.PackagesFlag, Reply.StringArray),
        "addUserRestriction" to Op(BlockKind.UserRestriction, Args.Key, Reply.Void, blocked = true),
        "clearUserRestriction" to
                Op(BlockKind.UserRestriction, Args.Key, Reply.Void, blocked = false),
        "setUserRestriction" to Op(BlockKind.UserRestriction, Args.KeyFlag, Reply.Void),
        "setCameraDisabled" to
                Op(BlockKind.DeviceState, Args.Flag, Reply.Void, key = DeviceState.Camera.key),
        "setScreenCaptureDisabled" to
                Op(BlockKind.DeviceState, Args.Flag, Reply.Void, key = DeviceState.ScreenCapture.key),
        "setMasterVolumeMuted" to
                Op(BlockKind.DeviceState, Args.Flag, Reply.Void, key = DeviceState.MasterVolume.key),
        "setStatusBarDisabled" to
                Op(BlockKind.DeviceState, Args.Flag, Reply.Bool, key = DeviceState.StatusBar.key)
    )

    /**
     * Reading state changes nothing, and a client that cannot read it cannot show the profile
     * what it is doing. Named one by one rather than by an "is" or "get" prefix, because plenty of
     * other things that only read are nobody's business - the security log, for one.
     */
    private val reads = setOf(
        "isApplicationHidden", "isPackageSuspended", "isUninstallBlocked", "getUserRestrictions",
        "getCameraDisabled", "getScreenCaptureDisabled", "isMasterVolumeMuted",
        "isStatusBarDisabled", "getUserControlDisabledPackages", "getMeteredDataDisabledPackages"
    )

    /** What [name] asks for, read out of [data]; [Call.Refused] whenever that is not certain. */
    fun decode(name: String, data: Parcel): Call {
        if (name in reads) return Call.PassThrough
        val op = writes[name] ?: return Call.Refused
        val parses = parse(op, data)
        val first = parses.firstOrNull() ?: return Call.Refused
        // Two readings that disagree mean the shape is not known well enough to act on
        if (parses.any { it != first }) return Call.Refused
        return first
    }

    /**
     * Every reading of [data] that fits [op]. The optional parts are what moves between versions:
     * the calling package was added to most of these calls, and a trailing flag for whether the
     * call is meant for the parent profile. Rather than track which version has which, every
     * combination is tried and only the ones that consume the payload exactly are kept.
     */
    private fun parse(op: Op, data: Parcel): List<Call.Block> {
        val start = data.dataPosition()
        val found = mutableListOf<Call.Block>()
        for (admin in listOf(true, false)) {
            for (caller in listOf(false, true)) {
                for (tail in 0..1) {
                    data.setDataPosition(start)
                    val call = try {
                        read(op, data, admin, caller, tail)
                    } catch (e: Exception) {
                        null
                    }
                    if (call != null) found += call
                }
            }
        }
        data.setDataPosition(start)
        return found
    }

    /** One reading, or null if it does not hold together. */
    private fun read(op: Op, p: Parcel, admin: Boolean, caller: Boolean, tail: Int): Call.Block? {
        if (admin) {
            // A nullable parcelable is a flag and then, for a ComponentName, two strings
            if (p.readInt() != 0) {
                p.readString()
                p.readString()
            }
        }
        if (caller) p.readString()
        val keys: List<String>
        val blocked: Boolean
        when (op.args) {
            Args.PackageFlag -> {
                keys = listOf(p.readString() ?: return null)
                blocked = p.readInt() != 0
            }
            Args.PackagesFlag -> {
                keys = p.createStringArray()?.toList() ?: return null
                blocked = p.readInt() != 0
            }
            Args.Key -> {
                keys = listOf(p.readString() ?: return null)
                blocked = op.blocked ?: return null
            }
            Args.KeyFlag -> {
                keys = listOf(p.readString() ?: return null)
                blocked = p.readInt() != 0
            }
            Args.Flag -> {
                keys = listOf(op.key ?: return null)
                blocked = p.readInt() != 0
            }
        }
        // A trailing flag is the call being aimed at the parent profile. Reading it as false is
        // the only case that means what this would do, so a set one is left to the admin
        repeat(tail) { if (p.readInt() != 0) return null }
        if (p.dataAvail() != 0) return null
        if (keys.isEmpty()) return null
        if (op.args != Args.Flag && keys.any { !validKey(op.kind, it) }) return null
        return Call.Block(op.kind, keys, blocked, op.reply)
    }

    /**
     * Whether [key] looks like something [kind] is keyed by. This is what makes a reading that
     * lands on the wrong field fail instead of being acted on: the calling package name would
     * pass as a package, but then the payload does not run out where it should.
     */
    private fun validKey(kind: BlockKind, key: String): Boolean = when (kind) {
        BlockKind.Hidden, BlockKind.Suspended, BlockKind.UninstallBlocked, BlockKind.Ucd,
        BlockKind.Mdd -> key.isValidPackageName
        BlockKind.UserRestriction -> restrictionKey.matches(key)
        BlockKind.DeviceState -> DeviceState.of(key) != null
    }

    private val restrictionKey = Regex("""^[a-z][a-z0-9_]{2,63}$""")
}

/**
 * The name the framework itself gives a transaction code on the device policy service.
 *
 * A code is an ordinal the AIDL compiler hands out in declaration order, so it moves whenever a
 * function is added anywhere above it - a table of codes written today would quietly point at the
 * wrong functions on the next Android version. The generated stub carries the mapping, and asking
 * it is the one way to get an answer that cannot go stale. [MyApplication] already lifts the
 * non-SDK restrictions this needs.
 *
 * When the mapping cannot be had, [nameOf] returns null for everything and the caller refuses: a
 * profile's grant stops working, rather than working on the wrong function.
 */
object DpmTransactions {
    private val names = mutableMapOf<Int, String?>()
    private var resolver: java.lang.reflect.Method? = null
    private var looked = false

    /** Whether the mapping is available at all, which decides if a profile's grant can be kept to. */
    fun available(): Boolean = synchronized(this) { resolve() != null }

    /** The function [code] stands for, or null if that cannot be established. */
    fun nameOf(code: Int): String? = synchronized(this) {
        val method = resolve() ?: return null
        names.getOrPut(code) {
            try {
                method.invoke(null, code) as? String
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun resolve(): java.lang.reflect.Method? {
        if (looked) return resolver
        looked = true
        resolver = try {
            Class.forName("${DhizukuBridge.DPM_DESCRIPTOR}\$Stub")
                .getMethod("getDefaultTransactionName", Int::class.javaPrimitiveType)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
        return resolver
    }
}
