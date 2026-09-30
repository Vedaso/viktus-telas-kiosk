package uk.nktnet.webviewkiosk.viktus

import android.util.Base64 as AndroidBase64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Viktus Telas fork: PBKDF2-HMAC-SHA256 by hand over javax.crypto.Mac.
 *
 * The maintenance PIN hash comes from the server (viktus-telas, `hashPin`) as
 * `pbkdf2$<iter>$<salt-b64>$<hash-b64>` and is checked here offline: changing the
 * Wi-Fi is exactly when the TV cannot reach the server. `PBKDF2WithHmacSHA256`
 * only exists from API 26 and minSdk is 21, so one code path for every stick.
 * Test vector shared with the server test: Pbkdf2Test.
 */
object Pbkdf2 {
    fun sha256(senha: String, salt: ByteArray, iteracoes: Int, bytes: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(senha.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val saida = ByteArray(bytes)
        var bloco = 1
        var pos = 0
        while (pos < bytes) {
            mac.update(salt)
            mac.update(byteArrayOf((bloco ushr 24).toByte(), (bloco ushr 16).toByte(), (bloco ushr 8).toByte(), bloco.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iteracoes) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, bytes - pos)
            System.arraycopy(t, 0, saida, pos, n)
            pos += n
            bloco++
        }
        return saida
    }

    /** True when [pin] matches [armazenado]; constant-time on the hash bytes. */
    fun confere(pin: String, armazenado: String, decode: (String) -> ByteArray = ::b64): Boolean {
        val partes = armazenado.split("$")
        if (partes.size != 4 || partes[0] != "pbkdf2") return false
        val iter = partes[1].toIntOrNull() ?: return false
        if (iter < 1 || iter > 1_000_000) return false
        val salt = runCatching { decode(partes[2]) }.getOrNull() ?: return false
        val esperado = runCatching { decode(partes[3]) }.getOrNull() ?: return false
        val calc = sha256(pin, salt, iter, esperado.size)
        var diff = 0
        for (i in calc.indices) diff = diff or (calc[i].toInt() xor esperado[i].toInt())
        return diff == 0
    }

    private fun b64(s: String): ByteArray = AndroidBase64.decode(s, AndroidBase64.DEFAULT)
}
