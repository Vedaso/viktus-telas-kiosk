package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import uk.nktnet.webviewkiosk.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Viktus Telas fork: `window.ViktusTelas`, the bridge the player page uses to update this app.
 *
 * The page decides *when* (the panel flags a TV, or the TV is on the pairing screen); this
 * class does the mechanics: download the APK, check its SHA-256, hand it to the Android
 * installer. The installer shows its own dialog and someone confirms it with the remote.
 * Only APKs served by our own host are accepted, and only with a matching hash, so a foreign
 * page reaching this bridge could at most reinstall our app.
 *
 * The "install unknown apps" permission is granted once by the provisioning script over ADB
 * (`appops set <pkg> REQUEST_INSTALL_PACKAGES allow`); without it we open the system screen
 * where it is toggled with the remote.
 */
class ViktusTelasInterface(private val context: Context) {
    companion object {
        const val NAME = "ViktusTelas"
        private const val HOST_PERMITIDO = "https://telas.viktus.com.br/"
        @Volatile private var estadoAtual = "ocioso"
        @Volatile private var emAndamento = false
    }

    @Suppress("unused")
    @JavascriptInterface
    fun versao(): String = BuildConfig.VERSION_NAME

    /** The stick's IPv4 on the LAN (first non-loopback), or "" : the pairing screen shows it so the kit and support get it without Settings. */
    @Suppress("unused")
    @JavascriptInterface
    fun ip(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress ?: ""
        } catch (_: Exception) { "" }
    }

    /** Last status: ocioso · baixando · verificando · instalando · erro: <motivo>. */
    @Suppress("unused")
    @JavascriptInterface
    fun estado(): String = estadoAtual

    /** Starts the update; returns at once. Poll estado() to follow it. */
    @Suppress("unused")
    @JavascriptInterface
    fun atualizar(url: String, sha256: String): String {
        if (!url.startsWith(HOST_PERMITIDO)) return "erro: url fora do host permitido"
        if (emAndamento) return estadoAtual
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            // Not pre-granted by ADB: open the toggle so the remote can allow it, then the page retries.
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            estadoAtual = "erro: sem permissão para instalar; libere no controle e tente de novo"
            return estadoAtual
        }
        emAndamento = true
        estadoAtual = "baixando"
        thread(name = "viktus-telas-update") {
            try {
                val dir = File(context.cacheDir, "apk").apply { mkdirs() }
                val apk = File(dir, "viktus-telas-update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 60_000
                conn.setRequestProperty("User-Agent", "ViktusTelas/" + BuildConfig.VERSION_NAME)
                if (conn.responseCode != 200) throw IllegalStateException("http ${conn.responseCode}")
                conn.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
                estadoAtual = "verificando"
                val md = MessageDigest.getInstance("SHA-256")
                apk.inputStream().use { s -> val b = ByteArray(65536); var n: Int; while (s.read(b).also { n = it } > 0) md.update(b, 0, n) }
                val hash = md.digest().joinToString("") { "%02x".format(it) }
                if (!hash.equals(sha256, ignoreCase = true)) { apk.delete(); throw IllegalStateException("sha256 não confere") }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
                estadoAtual = "instalando"
                // ACTION_INSTALL_PACKAGE goes straight to the package installer; a plain VIEW on an APK
                // opened an "Open with" chooser on a stick that also had a file manager (18/09).
                @Suppress("DEPRECATION")
                context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true) // installer finishes itself instead of parking on "App installed"
                    // CLEAR_TASK: a leftover installer screen from the previous update swallowed the new request (18/09)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: Exception) {
                estadoAtual = "erro: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                emAndamento = false
            }
        }
        return estadoAtual
    }
}
