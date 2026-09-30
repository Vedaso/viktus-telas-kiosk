package uk.nktnet.webviewkiosk.viktus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class Pbkdf2Test {
    // Same vector as viktus-telas src/dominio/dominio.test.ts: PIN 4821, salt bytes 0..15, 10.000 iterations.
    private val vetor = "pbkdf2\$10000\$AAECAwQFBgcICQoLDA0ODw==\$ai3UbCeDBT5nKdEHXGutZKoXwZELT3vm4xIneno6vN8="
    private val dec: (String) -> ByteArray = { Base64.getDecoder().decode(it) }

    @Test fun vetorDoServidor() {
        val salt = ByteArray(16) { it.toByte() }
        val hash = Base64.getEncoder().encodeToString(Pbkdf2.sha256("4821", salt, 10_000))
        assertEquals("ai3UbCeDBT5nKdEHXGutZKoXwZELT3vm4xIneno6vN8=", hash)
    }

    @Test fun confere() {
        assertTrue(Pbkdf2.confere("4821", vetor, dec))
        assertFalse(Pbkdf2.confere("4822", vetor, dec))
        assertFalse(Pbkdf2.confere("0000", vetor, dec))
    }

    @Test fun formatoRuim() {
        assertFalse(Pbkdf2.confere("4821", "", dec))
        assertFalse(Pbkdf2.confere("4821", "sha1\$10000\$AA==\$AA==", dec))
        assertFalse(Pbkdf2.confere("4821", "pbkdf2\$x\$AA==\$AA==", dec))
    }
}
