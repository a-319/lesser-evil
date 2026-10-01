package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time declaration of the framework's hidden interface. This module is consumed with
 * compileOnly, so this class is not packaged: the real one loads from the boot class loader.
 */
public interface IPackageInstaller extends IInterface {
    abstract class Stub extends Binder implements IPackageInstaller {
        public static IPackageInstaller asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
