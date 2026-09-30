package uk.nktnet.webviewkiosk.viktus

import android.app.Activity
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val NAVY = Color(0xFF0E1330)
private val BONE = Color(0xFFF2EEE3)
private val VERDE = Color(0xFF39FF14)
private val CINZA = Color(0xFFA9B0C7)
private val ALERTA = Color(0xFFFF8A80)

/**
 * Viktus Telas fork: the maintenance screen, drawn over the player.
 * PIN first (D-pad keypad, or the remote's number keys), then a 3-option menu.
 * Also the native "no internet" screen when the player page could not load at all.
 */
@Composable
fun ManutencaoOverlay(activity: Activity, naPlayer: Boolean, abrirConfiguracoes: () -> Unit) {
    if (naPlayer && ModoLoja.erroDeCarga && !ModoLoja.dialogo) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Viktus Telas", color = BONE, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(24.dp))
                Text("Sem internet", color = ALERTA, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Segure Voltar por 5 segundos para configurar a rede", color = BONE, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
        }
    }
    if (!ModoLoja.dialogo) return
    val store = remember { ManutencaoStore(activity) }
    var etapaMenu by remember { mutableStateOf(false) }
    var interacao by remember { mutableIntStateOf(0) }
    // Closes by itself after 60 s untouched (only the dialog: a store-mode exit is not undone here).
    LaunchedEffect(interacao) { delay(60_000); ModoLoja.dialogo = false }
    Dialog(
        onDismissRequest = { ModoLoja.dialogo = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .width(560.dp)
                .background(NAVY, RoundedCornerShape(16.dp))
                .padding(28.dp)
                .onPreviewKeyEvent { interacao++; false },
        ) {
            if (!etapaMenu) TecladoPin(store, onOk = { etapaMenu = true; interacao++ })
            else Menu(activity, store, abrirConfiguracoes)
        }
    }
}

@Composable
private fun TecladoPin(store: ManutencaoStore, onOk: () -> Unit) {
    var digitos by remember { mutableStateOf("") }
    var mensagem by remember { mutableStateOf("") }
    var bloqueadoMs by remember { mutableStateOf(store.restanteBloqueioMs()) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    LaunchedEffect(bloqueadoMs > 0) {
        while (bloqueadoMs > 0) { delay(1_000); bloqueadoMs = store.restanteBloqueioMs() }
        if (mensagem.startsWith("Aguarde")) mensagem = ""
    }
    fun digitar(d: String) {
        if (bloqueadoMs > 0 || digitos.length >= 4) return
        digitos += d
        if (digitos.length == 4) {
            when (val r = store.conferir(digitos)) {
                is ManutencaoStore.Resultado.Ok -> onOk()
                is ManutencaoStore.Resultado.Errado -> mensagem = "PIN incorreto"
                is ManutencaoStore.Resultado.Bloqueado -> { bloqueadoMs = r.restanteMs; mensagem = "" }
            }
            digitos = ""
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.onPreviewKeyEvent { e ->
            // Remotes with number keys type straight in; the on-screen keypad covers the ones without.
            val code = e.nativeKeyEvent.keyCode
            if (e.type == KeyEventType.KeyDown && code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9) {
                digitar((code - AndroidKeyEvent.KEYCODE_0).toString()); true
            } else if (e.type == KeyEventType.KeyDown && code == AndroidKeyEvent.KEYCODE_DEL) {
                digitos = digitos.dropLast(1); true
            } else false
        },
    ) {
        Text("Manutenção", color = BONE, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Digite o PIN da rede", color = CINZA, fontSize = 16.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until 4) {
                Box(
                    Modifier.size(44.dp).background(if (i < digitos.length) VERDE else Color(0xFF232A4D), RoundedCornerShape(10.dp)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val aviso = if (bloqueadoMs > 0) "Muitas tentativas. Aguarde ${formatar(bloqueadoMs)}" else mensagem
        Text(aviso, color = ALERTA, fontSize = 16.sp, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(8.dp))
        val linhas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✕"))
        for ((li, linha) in linhas.withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                for ((ci, tecla) in linha.withIndex()) {
                    val mod = Modifier.width(96.dp).height(52.dp).let { if (li == 0 && ci == 0) it.focusRequester(foco) else it }
                    Button(
                        onClick = {
                            when (tecla) {
                                "⌫" -> digitos = digitos.dropLast(1)
                                "✕" -> ModoLoja.dialogo = false
                                else -> digitar(tecla)
                            }
                        },
                        modifier = mod,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232A4D), contentColor = BONE),
                    ) { Text(tecla, fontSize = 22.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
private fun Menu(activity: Activity, store: ManutencaoStore, abrirConfiguracoes: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    val cores = ButtonDefaults.buttonColors(containerColor = Color(0xFF232A4D), contentColor = BONE)
    val largura = Modifier.fillMaxWidth().height(60.dp)
    Column {
        Text("Manutenção", color = BONE, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Button(onClick = { ModoLoja.trocarWifi(activity, store) }, modifier = largura.focusRequester(foco), colors = cores) {
            Text("Trocar Wi-Fi", fontSize = 20.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { ModoLoja.reiniciar(activity, store) }, modifier = largura, colors = cores) {
            Text(if (ModoLoja.reiniciaAparelho(activity)) "Reiniciar" else "Reiniciar app", fontSize = 20.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { ModoLoja.sairDoModoLoja(activity, store, abrirConfiguracoes) }, modifier = largura, colors = cores) {
            Text("Sair do modo loja", fontSize = 20.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Fora do modo loja, a TV fica livre até voltar ao player.", color = CINZA, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { ModoLoja.dialogo = false }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Fechar", color = BONE, fontSize = 18.sp)
        }
    }
}

private fun formatar(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s >= 60) "${s / 60} min ${"%02d".format(s % 60)} s" else "$s s"
}
