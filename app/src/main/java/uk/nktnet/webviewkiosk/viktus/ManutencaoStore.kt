package uk.nktnet.webviewkiosk.viktus

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Viktus Telas fork: what the maintenance PIN needs to survive offline and reboots.
 *
 * - The PIN hash arrives from the player poll (`ViktusTelas.definirPin`) and is never
 *   erased: unpairing or a server without answer must not leave the stick open.
 *   Until the first hash arrives the app default applies, `0000` (Victor, 30/09).
 * - Wrong attempts lock the keypad for 1, 2, 4, 8 then 15 minutes, every 5 errors.
 *   Counted on the monotonic clock and persisted as time left, so neither changing the
 *   date nor rebooting shortens it (after a reboot it resumes from what was left).
 * - Events queue here and leave on the next good poll (`eventosManutencao` / `confirmarEventos`).
 */
class ManutencaoStore(context: Context) {
    companion object {
        const val PIN_PADRAO = "0000"
        private const val PREFS = "viktus_manutencao"
        private const val K_PIN = "pin_hash"
        private const val K_ERROS = "erros"
        private const val K_BLOQUEIOS = "bloqueios"
        private const val K_BLOQ_ATE = "bloqueado_ate"
        private const val K_BLOQ_RESTANTE = "bloqueado_restante"
        private const val K_BLOQ_MARCO = "bloqueado_marco"
        private const val K_FILA = "fila"
        private const val MAX_FILA = 200
        private val ESCADA_MIN = longArrayOf(1, 2, 4, 8, 15)
    }

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed class Resultado {
        object Ok : Resultado()
        data class Errado(val restantes: Int) : Resultado()
        data class Bloqueado(val restanteMs: Long) : Resultado()
    }

    /** Stores the hash when it changed; null/blank keeps what the stick already has. */
    fun definirPin(hash: String?): String {
        if (hash.isNullOrBlank() || !hash.startsWith("pbkdf2$")) return "ignorado"
        if (prefs.getString(K_PIN, null) == hash) return "igual"
        prefs.edit().putString(K_PIN, hash).commit()
        return "ok"
    }

    fun usaPinPadrao(): Boolean = prefs.getString(K_PIN, null) == null

    fun restanteBloqueioMs(): Long {
        val ate = prefs.getLong(K_BLOQ_ATE, 0)
        if (ate == 0L) return 0
        // Remaining by the monotonic clock since the lock started; if the device rebooted
        // (elapsedRealtime went back) fall back to the stored remaining time.
        val marco = prefs.getLong(K_BLOQ_MARCO, 0)
        val restanteSalvo = prefs.getLong(K_BLOQ_RESTANTE, 0)
        val agoraMono = SystemClock.elapsedRealtime()
        // Wall clock is ignored on purpose: setting the date forward must not unlock the keypad.
        val r = if (agoraMono >= marco) restanteSalvo - (agoraMono - marco) else restanteSalvo
        if (r <= 0) { prefs.edit().remove(K_BLOQ_ATE).remove(K_BLOQ_RESTANTE).remove(K_BLOQ_MARCO).apply(); return 0 }
        // Persist progress so a reboot only replays what is left.
        prefs.edit().putLong(K_BLOQ_RESTANTE, r).putLong(K_BLOQ_MARCO, agoraMono).apply()
        return r
    }

    fun conferir(pin: String): Resultado {
        val restante = restanteBloqueioMs()
        if (restante > 0) return Resultado.Bloqueado(restante)
        val hash = prefs.getString(K_PIN, null)
        val ok = if (hash == null) pin == PIN_PADRAO else Pbkdf2.confere(pin, hash)
        if (ok) {
            prefs.edit().putInt(K_ERROS, 0).putInt(K_BLOQUEIOS, 0).commit()
            registrar("entrou")
            return Resultado.Ok
        }
        registrar("pin_errado")
        val erros = prefs.getInt(K_ERROS, 0) + 1
        if (erros >= 5) {
            val n = prefs.getInt(K_BLOQUEIOS, 0)
            val ms = ESCADA_MIN[minOf(n, ESCADA_MIN.size - 1)] * 60_000L
            prefs.edit().putInt(K_ERROS, 0).putInt(K_BLOQUEIOS, n + 1)
                .putLong(K_BLOQ_ATE, System.currentTimeMillis() + ms)
                .putLong(K_BLOQ_RESTANTE, ms)
                .putLong(K_BLOQ_MARCO, SystemClock.elapsedRealtime())
                .commit()
            registrar("bloqueio_pin")
            return Resultado.Bloqueado(ms)
        }
        prefs.edit().putInt(K_ERROS, erros).commit()
        return Resultado.Errado(5 - erros)
    }

    /** Queues an event; commit() so a reboot right after still sends it. */
    @Synchronized
    fun registrar(tipo: String) {
        val fila = fila()
        fila.put(JSONObject().put("id", UUID.randomUUID().toString()).put("tipo", tipo).put("ocorrido_em", isoAgora()))
        while (fila.length() > MAX_FILA) fila.remove(0)
        prefs.edit().putString(K_FILA, fila.toString()).commit()
    }

    @Synchronized
    fun eventosJson(): String {
        val fila = fila()
        if (fila.length() <= 50) return fila.toString()
        val parte = JSONArray()
        for (i in 0 until 50) parte.put(fila.get(i))
        return parte.toString()
    }

    @Synchronized
    fun confirmar(idsJson: String): String {
        val ids = runCatching { JSONArray(idsJson) }.getOrNull() ?: return "erro"
        val aceitos = HashSet<String>()
        for (i in 0 until ids.length()) aceitos.add(ids.optString(i))
        val fila = fila()
        val resto = JSONArray()
        for (i in 0 until fila.length()) {
            val e = fila.optJSONObject(i) ?: continue
            if (e.optString("id") !in aceitos) resto.put(e)
        }
        prefs.edit().putString(K_FILA, resto.toString()).commit()
        return "ok"
    }

    private fun fila(): JSONArray = runCatching { JSONArray(prefs.getString(K_FILA, "[]")) }.getOrElse { JSONArray() }

    private fun isoAgora(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
