package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager

// Viktus Telas fork: the Wi-Fi button opens only the network screen, no PIN, even while locked.
// As device owner we allowlist the settings package just while that screen is open and revoke it
// as soon as MainActivity resumes, so the rest of Android Settings (reset, uninstall) stays behind
// Unlock + PIN. Victor, 30/09: "no modo locked nao poderia deixar o app settings desbloqueado apenas?"
object WifiAccess {
    private const val PREFS = "viktus_wifi_access"
    private const val KEY_PACKAGE = "granted_package"

    fun open(activity: Activity) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pkg = activity.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (pkg == null) {
            ToastManager.show(activity, "Wi-Fi settings not found on this device")
            return
        }
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (getIsLocked(am) && DeviceOwnerManager.hasOwnerPermission(activity)) {
            try {
                val current = allowedPackages(activity)
                if (current.add(pkg)) {
                    // Remember what we added (persisted: the process may die while Settings is open).
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_PACKAGE, pkg).commit()
                    current.add(activity.packageName)
                    DeviceOwnerManager.DPM.setLockTaskPackages(DeviceOwnerManager.DAR, current.toTypedArray())
                }
            } catch (e: Exception) {
                Log.e(Constants.APP_SCHEME, "Failed to allow Wi-Fi settings in lock task", e)
            }
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "Failed to open Wi-Fi settings", e)
            ToastManager.show(activity, "Failed to open Wi-Fi settings: ${e.message}")
            revoke(activity)
        }
    }

    // getLockTaskPackages is API 26+; below that (the RK322x sticks run Android 7.1) the allowlist
    // is only ever this app, as in setupLockTaskPackage.
    private fun allowedPackages(context: Context): MutableSet<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DeviceOwnerManager.DPM.getLockTaskPackages(DeviceOwnerManager.DAR).toMutableSet()
        } else {
            mutableSetOf(context.packageName)
        }

    // Called from MainActivity.onResume: back from the Wi-Fi screen, the settings package leaves the allowlist.
    fun revoke(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pkg = prefs.getString(KEY_PACKAGE, null) ?: return
        try {
            if (DeviceOwnerManager.hasOwnerPermission(context)) {
                val current = allowedPackages(context)
                current.remove(pkg)
                current.add(context.packageName)
                DeviceOwnerManager.DPM.setLockTaskPackages(DeviceOwnerManager.DAR, current.toTypedArray())
            }
            prefs.edit().remove(KEY_PACKAGE).commit()
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "Failed to revoke Wi-Fi settings from lock task", e)
        }
    }
}
