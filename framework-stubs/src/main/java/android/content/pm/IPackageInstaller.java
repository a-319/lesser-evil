package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Enough of the framework's hidden package installer interface to name at compile time. The real
 * one is what loads at runtime; this is never packaged.
 */
public interface IPackageInstaller extends IInterface {
    abstract class Stub extends Binder implements IPackageInstaller {
        public static IPackageInstaller asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
