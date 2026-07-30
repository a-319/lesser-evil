package lesser.evil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

// Managed configurations are described by the app declaring them, which for browsers and most
// other apps means English only. Translating those descriptions is no reason for this app to go
// online, so the text is handed over to a translator already installed on the device through the
// system's text processing action, the very same action that shows up next to copy & paste when
// text is selected. An app answering it may hand the translated text back, which is what lets the
// translation be shown right where the original was.
//
// Text processing arrived in Android 6, so on older releases nothing answers the action and the
// translate action is simply never offered.

private const val PLAIN_TEXT = "text/plain"

private fun processTextIntent(text: String? = null) = Intent(Intent.ACTION_PROCESS_TEXT).apply {
    type = PLAIN_TEXT
    if (text != null) {
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        // Declaring the text editable is what asks for a translation to come back to us, a
        // translator treating it as read-only just shows the translation and returns nothing
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
    }
}

private fun Context.textProcessors(): List<ResolveInfo> =
    packageManager.queryIntentActivities(processTextIntent(), 0)
        .filter { it.activityInfo.packageName != packageName }

/** Whether any app on the device offers to process, and with that to translate, a text */
fun Context.canTranslateText() = textProcessors().isNotEmpty()

/** An [Intent] handing [text] over to be translated, or null when no app can take it */
fun Context.translateTextIntent(text: String): Intent? {
    val processors = textProcessors()
    val intent = processTextIntent(text)
    return when {
        processors.isEmpty() -> null
        // A lone translator is started right away, several of them are picked from by the user
        processors.size == 1 -> intent.apply {
            val info = processors.single().activityInfo
            component = ComponentName(info.packageName, info.name)
        }
        else -> Intent.createChooser(intent, getString(R.string.translate_description))
    }
}

/** The translation an app handed back, or null when it returned without one */
fun translatedText(data: Intent?): String? =
    data?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.takeIf { it.isNotBlank() }
