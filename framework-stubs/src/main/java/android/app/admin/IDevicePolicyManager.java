package android.app.admin;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Enough of the framework's hidden device policy interface to name at compile time. The real one
 * is what loads at runtime; this is never packaged, so there is no second copy of it to confuse
 * either the runtime or a shrinker.
 */
public interface IDevicePolicyManager extends IInterface {
    abstract class Stub extends Binder implements IDevicePolicyManager {
        public static IDevicePolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
    int setGlobalPrivateDns(ComponentName who, int mode, String privateDnsHost);
}
