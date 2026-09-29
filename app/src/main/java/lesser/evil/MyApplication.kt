package lesser.evil

import android.app.Application
import android.os.Build.VERSION
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MyApplication : Application() {
    lateinit var myRepo: MyRepository
    override fun onCreate() {
        super.onCreate()
        // An exemption that does not take is otherwise silent, and everything reached by plain
        // reflection over a non-SDK member then fails for no visible reason. "L" begins every
        // class signature, so it is the same request written the other way
        if (VERSION.SDK_INT >= 28 && !HiddenApiBypass.setHiddenApiExemptions("")) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }
        SP = SharedPrefs(applicationContext)
        val dbHelper = MyDbHelper(this)
        myRepo = MyRepository(dbHelper)
        Privilege.initialize(applicationContext)
        NotificationUtils.createChannels(this)
    }
}

lateinit var SP: SharedPrefs
    private set
