package com.evkumanda.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg1 = Color(0xFF0B0F1A)
private val Bg2 = Color(0xFF1A1033)
private val CardBg = Color(0xFF171E33)
private val Accent = Color(0xFF8B6CFF)
private val Good = Color(0xFF3DDC97)
private val Muted = Color(0xFF8A93AD)
private val Warn = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg1,
                    surface = CardBg,
                    onSurface = Color.White,
                    onBackground = Color.White,
                ),
            ) {
                App(applicationContext)
            }
        }
    }
}

@Composable
fun App(ctx: Context) {
    val prefs = remember { Prefs(ctx) }
    val tv = remember { TvRemote(ctx) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("home") }
    var msg by remember { mutableStateOf("Hazır") }
    var pcUp by remember { mutableStateOf<Boolean?>(null) }
    var tvUp by remember { mutableStateOf<Boolean?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    fun act(label: String, block: () -> Unit) {
        scope.launch {
            msg = "$label…"
            msg = try {
                withContext(Dispatchers.IO) { block() }
                "$label ✓"
            } catch (e: Exception) {
                "$label başarısız: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun pcOn() = Wol.send(prefs.get(K.PC_MAC), prefs.get(K.PC_IP))
    fun pcOff() = Pc.shutdown(prefs.get(K.PC_IP), prefs.get(K.PC_USER), prefs.get(K.PC_PASS), prefs.get(K.PC_CMD))
    fun tvOn() = tv.powerOn(prefs.get(K.TV_IP), prefs.get(K.TV_MAC))
    fun tvOff() = tv.powerOff(prefs.get(K.TV_IP))
    fun ps5On() = Ps5.wake(prefs.get(K.PS5_IP), prefs.get(K.PS5_CRED))

    LaunchedEffect(Unit) {
        while (true) {
            val pcIp = prefs.get(K.PC_IP)
            val tvIp = prefs.get(K.TV_IP)
            pcUp = if (pcIp.isBlank()) null else withContext(Dispatchers.IO) { Net.up(pcIp, intArrayOf(22, 3389, 445, 135)) }
            tvUp = if (tvIp.isBlank()) null else withContext(Dispatchers.IO) { Net.up(tvIp, intArrayOf(6466, 8009, 8008)) }
            delay(6000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg1, Bg2))),
    ) {
        when (screen) {
            "settings" -> SettingsScreen(prefs, onBack = { screen = "home" }, onPair = {
                val ip = prefs.get(K.TV_IP)
                scope.launch {
                    msg = "TV ile eşleşme başlatılıyor…"
                    try {
                        withContext(Dispatchers.IO) { tv.startPairing(ip) }
                        showCode = true
                        msg = "TV ekranındaki 6 haneli kodu gir"
                    } catch (e: Exception) {
                        msg = "Eşleşme başlatılamadı: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }, msg = msg)

            "remote" -> RemoteScreen(
                msg = msg,
                onBack = { screen = "home" },
                onKey = { k -> act("Tuş") { tv.key(prefs.get(K.TV_IP), k) } },
                onLink = { url, name -> act(name) { tv.openLink(prefs.get(K.TV_IP), url) } },
            )

            else -> HomeScreen(
                msg = msg,
                pcUp = pcUp,
                tvUp = tvUp,
                onSettings = { screen = "settings" },
                onRemote = { screen = "remote" },
                onPcOn = { act("PC açılıyor") { pcOn() } },
                onPcOff = { act("PC kapatılıyor") { pcOff() } },
                onTvOn = { act("TV açılıyor") { tvOn() } },
                onTvOff = { act("TV kapatılıyor") { tvOff() } },
                onPsOn = { act("PS5 uyandırılıyor") { ps5On() } },
                onGame = {
                    act("Oyun modu") {
                        tvOn()
                        Thread.sleep(2000)
                        ps5On()
                    }
                },
                onMovie = {
                    act("Film modu") {
                        tvOn()
                        Thread.sleep(6000)
                        tv.openLink(prefs.get(K.TV_IP), "https://www.youtube.com")
                    }
                },
                onAllOff = {
                    act("Hepsi kapatılıyor") {
                        try { tvOff() } catch (_: Exception) {}
                        pcOff()
                    }
                },
            )
        }
    }

    if (showCode) {
        AlertDialog(
            onDismissRequest = { showCode = false },
            title = { Text("TV kodu") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("6 haneli kod") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = code
                    showCode = false
                    scope.launch {
                        msg = "Kod doğrulanıyor…"
                        msg = try {
                            val ok = withContext(Dispatchers.IO) { tv.finishPairing(c) }
                            if (ok) {
                                prefs.put(K.TV_PAIRED, "1")
                                "TV eşleşti ✓"
                            } else {
                                "Kod kabul edilmedi"
                            }
                        } catch (e: Exception) {
                            "Eşleşme başarısız: ${e.message ?: e.javaClass.simpleName}"
                        }
                        code = ""
                    }
                }) { Text("Eşleştir") }
            },
            dismissButton = { TextButton(onClick = { showCode = false }) { Text("İptal") } },
        )
    }
}

@Composable
fun HomeScreen(
    msg: String,
    pcUp: Boolean?,
    tvUp: Boolean?,
    onSettings: () -> Unit,
    onRemote: () -> Unit,
    onPcOn: () -> Unit,
    onPcOff: () -> Unit,
    onTvOn: () -> Unit,
    onTvOff: () -> Unit,
    onPsOn: () -> Unit,
    onGame: () -> Unit,
    onMovie: () -> Unit,
    onAllOff: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ev Kumanda", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Evdeki her şey tek yerden", color = Muted, fontSize = 14.sp)
            }
            TextButton(onClick = onSettings) { Text("⚙ Ayarlar", color = Accent) }
        }

        Text("Sahneler", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SceneButton("🎮", "Oyun", onGame, Modifier.weight(1f))
            SceneButton("🎬", "Film", onMovie, Modifier.weight(1f))
            SceneButton("🌙", "Hepsini kapat", onAllOff, Modifier.weight(1f))
        }

        Text("Cihazlar", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        DeviceCard(
            emoji = "📺",
            title = "Google TV",
            sub = "Aç/kapat, kumanda, uygulamalar",
            up = tvUp,
            onOn = onTvOn,
            onOff = onTvOff,
            extra = { TextButton(onClick = onRemote) { Text("Kumandayı aç ›", color = Accent) } },
        )
        DeviceCard(
            emoji = "🖥️",
            title = "Bilgisayar",
            sub = "Wake-on-LAN ile aç, SSH ile kapat",
            up = pcUp,
            onOn = onPcOn,
            onOff = onPcOff,
        )
        DeviceCard(
            emoji = "🎮",
            title = "PlayStation 5",
            sub = "Dinlenme modundan uyandır. Kapatma: TV ile (HDMI-CEC)",
            up = null,
            onOn = onPsOn,
            onOff = null,
        )

        Spacer(Modifier.height(4.dp))
        Text(msg, color = if (msg.contains("başarısız") || msg.contains("başlatılamadı")) Warn else Muted, fontSize = 13.sp)
    }
}

@Composable
fun SceneButton(emoji: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 26.sp)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DeviceCard(
    emoji: String,
    title: String,
    sub: String,
    up: Boolean?,
    onOn: () -> Unit,
    onOff: (() -> Unit)?,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 26.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = Muted, fontSize = 12.sp)
                }
                val dot = when (up) {
                    true -> Good
                    false -> Warn
                    null -> Muted
                }
                val label = when (up) {
                    true -> "açık"
                    false -> "kapalı"
                    null -> "—"
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, color = dot, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onOn, modifier = Modifier.weight(1f)) { Text("Aç") }
                if (onOff != null) {
                    OutlinedButton(onClick = onOff, modifier = Modifier.weight(1f)) { Text("Kapat") }
                }
            }
            if (extra != null) extra()
        }
    }
}

@Composable
fun RemoteScreen(
    msg: String,
    onBack: () -> Unit,
    onKey: (Int) -> Unit,
    onLink: (String, String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Geri", color = Accent) }
            Spacer(Modifier.weight(1f))
            Text("Google TV Kumanda", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(60.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyBtn("⏻") { onKey(TvRemote.KEY_POWER) }
            KeyBtn("⌂") { onKey(TvRemote.KEY_HOME) }
            KeyBtn("↩") { onKey(TvRemote.KEY_BACK) }
            KeyBtn("🔇") { onKey(TvRemote.KEY_MUTE) }
        }
        KeyBtn("▲") { onKey(TvRemote.KEY_UP) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyBtn("◀") { onKey(TvRemote.KEY_LEFT) }
            KeyBtn("OK", big = true) { onKey(TvRemote.KEY_OK) }
            KeyBtn("▶") { onKey(TvRemote.KEY_RIGHT) }
        }
        KeyBtn("▼") { onKey(TvRemote.KEY_DOWN) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyBtn("Ses −") { onKey(TvRemote.KEY_VOL_DOWN) }
            KeyBtn("⏯") { onKey(TvRemote.KEY_PLAY_PAUSE) }
            KeyBtn("Ses +") { onKey(TvRemote.KEY_VOL_UP) }
        }

        Text("Uygulamalar", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onLink("https://www.youtube.com", "YouTube") }) { Text("YouTube") }
            OutlinedButton(onClick = { onLink("https://www.netflix.com", "Netflix") }) { Text("Netflix") }
            OutlinedButton(onClick = { onLink("https://www.primevideo.com", "Prime Video") }) { Text("Prime") }
        }
        Text(msg, color = if (msg.contains("başarısız")) Warn else Muted, fontSize = 13.sp)
    }
}

@Composable
fun KeyBtn(label: String, big: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(if (big) 92.dp else 72.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) { Text(label, fontSize = if (label.length > 2) 13.sp else 22.sp) }
}

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onPair: () -> Unit, msg: String) {
    val s = remember {
        mutableStateMapOf<String, String>().apply { K.all.forEach { put(it, prefs.get(it)) } }
    }

    @Composable
    fun Field(label: String, key: String, password: Boolean = false) {
        OutlinedTextField(
            value = s[key] ?: "",
            onValueChange = { s[key] = it },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    fun save() {
        K.all.forEach { prefs.put(it, s[it] ?: "") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { save(); onBack() }) { Text("‹ Kaydet ve dön", color = Accent) }
        }
        Text("Ayarlar", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        Text("📺 Google TV", fontWeight = FontWeight.SemiBold)
        Field("TV IP adresi (ör. 192.168.1.20)", K.TV_IP)
        Field("TV MAC adresi (Wake-on-LAN için)", K.TV_MAC)
        Button(onClick = { save(); onPair() }, modifier = Modifier.fillMaxWidth()) { Text("TV'yi eşleştir (bir kez)") }
        Text(
            "TV'de: Ayarlar › Sistem › Ağ'da 'Wi-Fi/LAN ile uyandırma' ve ağ bekleme modunu aç. " +
                "Eşleştirmede TV ekranında 6 haneli kod çıkar.",
            color = Muted, fontSize = 12.sp,
        )

        Text("🖥️ Bilgisayar", fontWeight = FontWeight.SemiBold)
        Field("PC IP adresi", K.PC_IP)
        Field("PC MAC adresi", K.PC_MAC)
        Field("SSH kullanıcı adı", K.PC_USER)
        Field("SSH şifresi", K.PC_PASS, password = true)
        Field("Kapatma komutu", K.PC_CMD)
        Text(
            "Windows'ta kapatmak için 'OpenSSH Server' özelliğini kur. Linux'ta komut: sudo shutdown now",
            color = Muted, fontSize = 12.sp,
        )

        Text("🎮 PlayStation 5", fontWeight = FontWeight.SemiBold)
        Field("PS5 IP adresi", K.PS5_IP)
        Field("PS5 user-credential", K.PS5_CRED)
        Text(
            "PS5'te: Ayarlar › Sistem › Uzaktan Oynatma'yı aç; Güç tasarrufu › Dinlenme modunda kullanılabilir özellikler › " +
                "'İnternete bağlı kal' ve 'Ağdan PS5'i açmayı etkinleştir'. Kapatmak için Ayarlar › Sistem › HDMI › " +
                "'HDMI cihaz bağlantısını etkinleştir' aç, TV kapanınca PS5 de dinlenmeye geçer.",
            color = Muted, fontSize = 12.sp,
        )

        Button(onClick = { save(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Kaydet") }
        Text(msg, color = Muted, fontSize = 13.sp)
    }
}
