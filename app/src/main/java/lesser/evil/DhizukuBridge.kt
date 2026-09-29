package lesser.evil

import android.os.Parcel
import android.util.Log
import lesser.evil.dpm.isValidPackageName
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
    enum class Reply { Void, Bool, StringArray, StringList }

    /** A client's call, once it is known what it asks for. */
    sealed interface Call {
        /** A tracked block to run through the gateway as the client's actor. */
        data class Block(
            val kind: BlockKind, val keys: List<String>, val blocked: Boolean, val reply: Reply
        ) : Call
        /**
         * A kind whose only way to be changed is to hand over the list it should now hold. What
         * that asks for is a difference, so it is worked out against the device rather than the
         * call: the keys that are new are blocked, the ones that fell out are released, and each
         * side is the actor's to do or not.
         */
        data class WholeList(
            val kind: BlockKind, val keys: List<String>, val reply: Reply
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
        Flag,
        /** The whole list the kind should hold, with nothing to say which way anything goes */
        PackageList
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
                Op(BlockKind.DeviceState, Args.Flag, Reply.Bool, key = DeviceState.StatusBar.key),
        // These two have no single-entry form at all: the list is handed over whole, and the one
        // that answers says which entries it could not carry out - which is what a refusal is
        "setUserControlDisabledPackages" to Op(BlockKind.Ucd, Args.PackageList, Reply.Void),
        "setMeteredDataDisabledPackages" to Op(BlockKind.Mdd, Args.PackageList, Reply.StringList)
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

    /**
     * Every function this app can do anything with, so their codes can be looked up by name on the
     * versions that offer no lookup of their own. A getter rather than a stored set, so it cannot
     * be read before [writes] and [reads] are initialised.
     */
    val knownFunctions: Set<String> get() = writes.keys + reads

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
    private fun parse(op: Op, data: Parcel): List<Call> {
        val start = data.dataPosition()
        val found = mutableListOf<Call>()
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
    private fun read(op: Op, p: Parcel, admin: Boolean, caller: Boolean, tail: Int): Call? {
        if (admin) {
            // A nullable parcelable is a flag and then, for a ComponentName, two strings
            if (p.readInt() != 0) {
                p.readString()
                p.readString()
            }
        }
        if (caller) p.readString()
        // The whole-list form has no flag to read, and an empty list is a real request: it asks
        // for everything to be released, which for a profile means everything of its own
        if (op.args == Args.PackageList) {
            val list = p.createStringArray()?.toList() ?: return null
            repeat(tail) { if (p.readInt() != 0) return null }
            if (p.dataAvail() != 0) return null
            if (list.any { !validKey(op.kind, it) }) return null
            return Call.WholeList(op.kind, list.distinct(), op.reply)
        }
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
            Args.PackageList -> return null
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
 * Which function a transaction code on the device policy service stands for.
 *
 * A code is an ordinal the AIDL compiler hands out in declaration order, so it moves whenever a
 * function is added anywhere above it: hiding an application is code 125 on Android 9, 138 on 11
 * and 157 on 16, and 125 on 16 is setting the default SMS application. A table of codes written
 * today would therefore not merely stop working on the next version - it would carry out different
 * functions than the ones asked for. So the code is never the thing this trusts; the name is, and
 * the name is read off the generated stub itself.
 *
 * There are two ways to read it, and they cover every version between them:
 *
 *  - From Android 10 on, the stub carries getDefaultTransactionName, a generated lookup from code
 *    to name. It was added for tracing, not for dispatch, which is why older versions lack it.
 *  - On every version, the stub carries a TRANSACTION_<function> constant per function, because
 *    its own dispatch is written in terms of them. Read the ones this app knows about and the
 *    mapping falls out backwards.
 *
 * Both are members of a non-SDK class, so ordinary reflection only reaches them where the
 * process is exempt from the non-SDK restrictions - and an exemption that did not take is silent,
 * which is why neither is reached by ordinary reflection alone. Each is looked up plainly first,
 * for the versions that have no restrictions, and then through [HiddenApiBypass], which hands back
 * the member without going through the check that would refuse it.
 *
 * If neither can be had, [nameOf] answers null for everything and the caller refuses: a profile's
 * grant stops working, rather than working on the wrong function.
 */
object DpmTransactions {
    private val names = mutableMapOf<Int, String?>()
    private var lookup: java.lang.reflect.Method? = null
    private var byConstant: Map<Int, String> = emptyMap()
    private var looked = false
    /** How the mapping was reached, for saying why it is missing when it is. */
    var how: String = "not looked for yet"
        private set

    /** Whether the mapping is available at all, which decides if a profile's grant can be kept to. */
    fun available(): Boolean = synchronized(this) {
        look()
        lookup != null || byConstant.isNotEmpty()
    }

    /** The function [code] stands for, or null if that cannot be established. */
    fun nameOf(code: Int): String? = synchronized(this) {
        look()
        names.getOrPut(code) {
            val fromLookup = lookup?.let { method ->
                try {
                    method.invoke(null, code) as? String
                } catch (e: Throwable) {
                    null
                }
            }
            // The constants only cover the functions this app knows, which is all it ever asks
            // about; anything else is refused either way
            fromLookup ?: byConstant[code]
        }
    }

    private fun look() {
        if (looked) return
        looked = true
        val stub = try {
            Class.forName("${DhizukuBridge.DPM_DESCRIPTOR}\$Stub")
        } catch (e: Throwable) {
            e.printStackTrace()
            how = "the device policy interface could not be loaded: $e"
            return
        }
        lookup = findLookup(stub)
        byConstant = findConstants(stub)
        how = when {
            lookup != null && byConstant.isNotEmpty() ->
                "by name lookup and ${byConstant.size} constants"
            lookup != null -> "by name lookup"
            byConstant.isNotEmpty() -> "by ${byConstant.size} constants"
            else -> "neither the name lookup nor any constant could be reached"
        }
        Log.d("DpmTransactions", "mapping: $how")
    }

    /** The framework's own code-to-name lookup, from Android 10 on. */
    private fun findLookup(stub: Class<*>): java.lang.reflect.Method? {
        val int = Int::class.javaPrimitiveType
        try {
            return stub.getMethod("getDefaultTransactionName", int)
        } catch (e: Throwable) {
            // Either this version has no such method, or the restrictions hid it. Tell them apart
            // by asking again in a way the restrictions do not apply to
        }
        return try {
            HiddenApiBypass.getDeclaredMethod(stub, "getDefaultTransactionName", int)
        } catch (e: Throwable) {
            null
        }
    }

    /** The constant each function this app knows is dispatched by, which every version carries. */
    private fun findConstants(stub: Class<*>): Map<Int, String> {
        val wanted = DhizukuBridge.knownFunctions
        val plain = wanted.mapNotNull { name ->
            try {
                val field = stub.getDeclaredField("TRANSACTION_$name")
                field.isAccessible = true
                // A function this version does not have simply has no constant, and stays unknown
                (field.get(null) as? Int)?.let { it to name }
            } catch (e: Throwable) {
                null
            }
        }.toMap()
        if (plain.isNotEmpty()) return plain
        return try {
            HiddenApiBypass.getStaticFields(stub).mapNotNull { field ->
                val name = field.name.removePrefix("TRANSACTION_")
                if (name == field.name || name !in wanted) return@mapNotNull null
                field.isAccessible = true
                (field.get(null) as? Int)?.let { it to name }
            }.toMap()
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyMap()
        }
    }
}
