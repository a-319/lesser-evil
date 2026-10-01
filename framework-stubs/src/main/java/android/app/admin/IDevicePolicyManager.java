package android.app.admin;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time declaration of the framework's hidden interface. This module is consumed with
 * compileOnly, so this class is not packaged: the real one loads from the boot class loader.
 */
public interface IDevicePolicyManager extends IInterface {
    abstract class Stub extends Binder implements IDevicePolicyManager {
        public static IDevicePolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
    int setGlobalPrivateDns(ComponentName who, int mode, String privateDnsHost);
}
