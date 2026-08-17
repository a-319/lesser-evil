package lesser.evil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

// Managed configurations are described by the app declaring them, which for browsers and most
// other apps means English only. Translating those descriptions is no reason for this app to go
// online, so the text is handed over to a translator already installed on the device, which may
// hand the translation back to us. That is what lets the translation be shown right where the
// original was.

private const val PLAIN_TEXT = "text/plain"

// The ways of asking for a translation, the one that fits us best first. An app answering the
// translate action is there to translate a text handed to it, while text processing is the action
// of the selection toolbar, which an app may well only offer once the user turns it on.
private val TRANSLATE_ACTIONS = listOf(Intent.ACTION_TRANSLATE, Intent.ACTION_PROCESS_TEXT)

private fun translateIntent(action: String, text: String? = null) = Intent(action).apply {
    if (action == Intent.ACTION_PROCESS_TEXT) type = PLAIN_TEXT
    if (text == null) return@apply
    // The translate action takes the text as EXTRA_TEXT, text processing as EXTRA_PROCESS_TEXT
    val extra = when (action) {
        Intent.ACTION_TRANSLATE -> Intent.EXTRA_TEXT
        else -> Intent.EXTRA_PROCESS_TEXT
    }
    putExtra(extra, text)
    // Declaring the text editable is what asks for a translation to come back to us, a translator
    // treating it as read-only just shows the translation and returns nothing
    putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
}

private fun Context.translators(action: String): List<ResolveInfo> =
    packageManager.queryIntentActivities(translateIntent(action), 0)
        .filter { it.activityInfo.packageName != packageName }

/** Whether any app on the device offers to translate a text for us */
fun Context.canTranslateText() = TRANSLATE_ACTIONS.any { translators(it).isNotEmpty() }

/** An [Intent] handing [text] over to be translated, or null when no app can take it */
fun Context.translateTextIntent(text: String): Intent? {
    val (action, translators) = TRANSLATE_ACTIONS.firstNotNullOfOrNull { action ->
        translators(action).takeIf { it.isNotEmpty() }?.let { action to it }
    } ?: return null

    val intent = translateIntent(action, text)
    return when {
        // A lone translator is started right away, several of them are picked from by the user
        translators.size == 1 -> intent.apply {
            val info = translators.single().activityInfo
            component = ComponentName(info.packageName, info.name)
        }
        else -> Intent.createChooser(intent, getString(R.string.translate_description))
    }
}

/** The translation an app handed back, or null when it returned without one */
fun translatedText(data: Intent?): String? =
    data?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.takeIf { it.isNotBlank() }
