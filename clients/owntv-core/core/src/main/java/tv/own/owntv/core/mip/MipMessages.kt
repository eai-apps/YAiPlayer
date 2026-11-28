// miptv (operator edition of YAiPlayer). Original code. See docs/MIP_PLAYER.md.
package tv.own.owntv.core.mip

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shows the operator's messages one after another in the system dialog (D-pad friendly on a TV box, same on a phone)
 * and marks each as read once the user closed it. Call from a coroutine that lives as long as the user is signed in.
 */
suspend fun MipService.showMessages(context: Context) {
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() ?: return
    for (m in messages(activity)) {
        if (activity.isFinishing) return
        suspendCancellableCoroutine<Unit> { cont ->
            val dialog = AlertDialog.Builder(activity).setTitle(m.title).setMessage(m.body)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { if (cont.isActive) cont.resume(Unit) }
                .create()
            cont.invokeOnCancellation { runCatching { dialog.dismiss() } }
            dialog.show()
        }
        shown(m)
    }
}

/** Looks for messages now and then every [everyMillis] while the caller's coroutine lives. */
suspend fun MipService.watchMessages(context: Context, everyMillis: Long = 15 * 60 * 1000L) {
    while (true) {
        runCatching { showMessages(context) }
        delay(everyMillis)
    }
}
