package uk.nktnet.webviewkiosk.viktus

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.utils.getIsLocked
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import kotlin.system.exitProcess

/**
 * Viktus Telas fork: "modo loja". Once configured the stick never needs the remote:
 * the app opens locked and comes back if someone leaves it. The only way out is
 * holding Back for 5 s and typing the network PIN (ManutencaoDialog), which offers
 * Trocar Wi-Fi, Reiniciar and Sair do modo loja (decided by Victor, 30/09).
 *
 * Locked means Android lock task where the app is device owner (Home does nothing).
 * On firmwares that refuse device owner (Mi TV Stick, Android 9) a watchdog brings
 * the app back 3 s after it leaves the foreground. "Sair do modo loja" has no
 * timeout: it lasts until the player screen is shown again or the process restarts.
 */
object ModoLoja {
    private const val TAG = "ViktusModoLoja"
    const val SEGURAR_VOLTAR_MS = 5_000L
    private const val VIGIA_MS = 3_000L

    /** Maintenance dialog on screen (PIN, then menu). */
    var dialogo by mutableStateOf(false)
    /** The player page failed to load (no network at boot): the native offline screen shows. */
    var erroDeCarga by mutableStateOf(false)

    /** Left on purpose through the menu; only this process, only until the player shows again. */
    @Volatile var saiu = false
        private set
    @Volatile private var pausaAte = 0L
    @Volatile private var emPrimeiroPlano = false
    private val handler = Handler(Looper.getMainLooper())
    private var settingsLiberado: String? = null

    fun pausar(ms: Long) { pausaAte = SystemClock.elapsedRealtime() + ms }
    private fun pausado() = SystemClock.elapsedRealtime() < pausaAte

    fun abrirDialogo() { if (!saiu) dialogo = true }

    /** The player route is on screen again: the store mode is back. */
    fun voltouAoPlayer() { saiu = false }

    // ── Watchdog (only matters where lock task is not active) ──────────────────

    private val trazerDeVolta = Runnable {
        val ctx = contexto ?: return@Runnable
        if (emPrimeiroPlano || saiu || pausado()) return@Runnable
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {
            Log.w(TAG, "vigia: não trouxe o app de volta", e)
        }
    }
    private var contexto: Context? = null

    fun aoIniciar() { emPrimeiroPlano = true; handler.removeCallbacks(trazerDeVolta) }

    fun aoParar(activity: Activity) {
        emPrimeiroPlano = false
        contexto = activity.applicationContext
        if (saiu || pausado() || activity.isChangingConfigurations) return
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (getIsLocked(am)) return // lock task: Home is blocked, leaving is our own doing (installer, Wi-Fi)
        handler.removeCallbacks(trazerDeVolta)
        handler.postDelayed(trazerDeVolta, VIGIA_MS)
    }

    // ── Menu actions ───────────────────────────────────────────────────────────

    /** Opens only the Android Wi-Fi screen; Back there returns to the player. */
    fun trocarWifi(activity: Activity, store: ManutencaoStore) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pkg = activity.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        if (pkg != null && DeviceOwnerManager.hasOwnerPermission(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Lock task only lets allowlisted packages start: the settings app joins while the Wi-Fi screen is open.
            try {
                val atuais = DeviceOwnerManager.DPM.getLockTaskPackages(DeviceOwnerManager.DAR).toMutableSet()
                if (atuais.add(pkg)) {
                    DeviceOwnerManager.DPM.setLockTaskPackages(DeviceOwnerManager.DAR, atuais.toTypedArray())
                    settingsLiberado = pkg
                }
            } catch (e: Exception) { Log.w(TAG, "wifi: allowlist", e) }
        }
        store.registrar("wifi")
        pausar(10 * 60_000L) // the watchdog must not pull the player over the Wi-Fi screen
        dialogo = false
        try { activity.startActivity(intent) } catch (e: Exception) {
            Log.w(TAG, "wifi: sem tela de Wi-Fi", e)
            revogarSettings(activity)
        }
    }

    /** Back on our activity: the settings app leaves the lock task allowlist. */
    fun revogarSettings(context: Context) {
        val pkg = settingsLiberado ?: return
        settingsLiberado = null
        pausaAte = 0L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val atuais = DeviceOwnerManager.DPM.getLockTaskPackages(DeviceOwnerManager.DAR).toMutableSet()
            if (atuais.remove(pkg)) DeviceOwnerManager.DPM.setLockTaskPackages(DeviceOwnerManager.DAR, atuais.toTypedArray())
        } catch (e: Exception) { Log.w(TAG, "wifi: revogar", e) }
    }

    fun reiniciaAparelho(context: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && DeviceOwnerManager.hasOwnerPermission(context)

    /** Device owner: reboots the stick. Otherwise restarts the app process, which opens locked. */
    fun reiniciar(activity: Activity, store: ManutencaoStore) {
        store.registrar("reiniciar")
        dialogo = false
        if (reiniciaAparelho(activity)) {
            try { DeviceOwnerManager.DPM.reboot(DeviceOwnerManager.DAR); return } catch (e: Exception) { Log.w(TAG, "reboot", e) }
        }
        val launch = Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(activity, 7301, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        val am = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC, System.currentTimeMillis() + 1_500, pi)
        activity.finishAffinity()
        exitProcess(0)
    }

    /** No timeout: free until the player shows again or the app restarts (Victor, 30/09). */
    fun sairDoModoLoja(activity: Activity, store: ManutencaoStore, abrirConfiguracoes: () -> Unit) {
        saiu = true
        handler.removeCallbacks(trazerDeVolta)
        dialogo = false
        store.registrar("saiu_modo_loja")
        tryUnlockTask(activity)
        abrirConfiguracoes()
        Log.i(TAG, "saiu do modo loja (${Constants.APP_SCHEME})")
    }
}
