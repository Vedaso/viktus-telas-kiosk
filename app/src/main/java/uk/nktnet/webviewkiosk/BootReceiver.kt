package uk.nktnet.webviewkiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Viktus Telas fork: bring the player back after a power cut or a self-update.
 *
 * Upstream relies on being the HOME app to start on boot, but some TV firmwares
 * (Mi TV Stick, Android 9, measured 18/09/2026) refuse to change the launcher and
 * refuse device owner, so after a reboot the TV sat on the Google launcher. On
 * Android 9 an activity may be started from BOOT_COMPLETED; on 10+ the system may
 * block background starts, and then this receiver is simply a no-op.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED: come back after a self-update (the installer kills the old process)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
            // Background activity start blocked: nothing else to do here.
        }
    }
}
