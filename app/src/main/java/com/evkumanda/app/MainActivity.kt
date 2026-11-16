package com.evkumanda.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------------------------------------------------------- tema

private class Theme(val key: String, val name: String, val c1: Color, val c2: Color, val c3: Color, val accent: Color)

private val Themes = listOf(
    Theme("kirmizi", "Kırmızı", Color(0xFFE0482B), Color(0xFF8C1A12), Color(0xFF2B0808), Color(0xFFFF6A4D)),
    Theme("mor", "Mor", Color(0xFF7C5CFF), Color(0xFF3A2380), Color(0xFF120B2B), Color(0xFF9B8CFF)),
    Theme("mavi", "Mavi", Color(0xFF2F80ED), Color(0xFF123E8A), Color(0xFF071329), Color(0xFF5AA2FF)),
    Theme("yesil", "Yeşil", Color(0xFF1FB36B), Color(0xFF0E5B3A), Color(0xFF051F15), Color(0xFF3DDC97)),
)

@Composable
private fun isLandscape(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp
}

private fun themeOf(k: String): Theme = Themes.firstOrNull { it.key == k } ?: Themes[0]

private val Good = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF8A80)

private fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), a: Float = 0.12f): Modifier =
    this.clip(shape).background(Color.White.copy(alpha = a)).border(1.dp, Color.White.copy(alpha = 0.18f), shape)

private fun appName(pkg: String?): String = when (pkg) {
    null -> "Bilinmiyor"
    "com.netflix.ninja" -> "Netflix"
    "com.google.android.youtube.tv" -> "YouTube"
    "com.amazon.amazonvideo.livingroom" -> "Prime Video"
    "com.spotify.tv.android" -> "Spotify"
    "com.disney.disneyplus" -> "Disney+"
    "tv.twitch.android.app" -> "Twitch"
    "com.android.vending" -> "Play Store"
    "com.google.android.apps.tv.launcherx" -> "Ana ekran"
    "com.evkumanda.app.tv" -> "Ev TV"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

private class AppTile(val id: String, val label: String, val link: String?, val c1: Color, val c2: Color)

private val Tiles = listOf(
    AppTile("ps5", "PS5", null, Color(0xFF3B82F6), Color(0xFF1D4ED8)),
    AppTile("netflix", "Netflix", "https://www.netflix.com", Color(0xFF3A1111), Color(0xFFE50914)),
    AppTile("youtube", "YouTube", "https://www.youtube.com", Color(0xFF5A1414), Color(0xFFFF2D2D)),
    AppTile("prime", "Prime", "https://app.primevideo.com", Color(0xFF0B3C5D), Color(0xFF1FA5E0)),
    AppTile("spotify", "Spotify", "https://open.spotify.com", Color(0xFF0B3D24), Color(0xFF1DB954)),
    AppTile("disney", "Disney+", "https://www.disneyplus.com", Color(0xFF0A1A4A), Color(0xFF2B59D6)),
    AppTile("twitch", "Twitch", "https://www.twitch.tv", Color(0xFF2A1450), Color(0xFF9146FF)),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF6A4D), surface = Color(0xFF2B0808))) {
                App(applicationContext)
            }
        }
    }
}

// ---------------------------------------------------------------- kök

@Composable
fun App(ctx: Context) {
    val prefs = remember { Prefs(ctx) }
    val tv = remember { TvRemote(ctx) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("remote") }
    var themeKey by remember { mutableStateOf(prefs.get(K.THEME)) }
    var msg by remember { mutableStateOf("") }
    var pcUp by remember { mutableStateOf<Boolean?>(null) }
    var tvUp by remember { mutableStateOf<Boolean?>(null) }
    var ps5Up by remember { mutableStateOf<Boolean?>(null) }
    var finding by remember { mutableStateOf(false) }
    var nowApp by remember { mutableStateOf<String?>(null) }
    var tvOnState by remember { mutableStateOf<Boolean?>(null) }
    var selectedTile by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val theme = themeOf(themeKey)

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

    fun quiet(block: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                msg = "Hata: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun pcOn() = Wol.send(prefs.get(K.PC_MAC), prefs.get(K.PC_IP))
    fun pcOff() = Pc.shutdown(prefs.get(K.PC_IP), prefs.get(K.PC_USER), prefs.get(K.PC_PASS), prefs.get(K.PC_CMD))
    fun tvOn() = tv.powerOn(prefs.get(K.TV_IP), prefs.get(K.TV_MAC))
    fun tvOff() = tv.powerOff(prefs.get(K.TV_IP))
    fun ps5On() = Ps5.wake(prefs.get(K.PS5_IP), prefs.get(K.PS5_CRED))

    /** Ağdaki cihazları bulur. Boş alanları doldurur; [force] ise bulunanla üzerine yazar. */
    fun discover(force: Boolean) {
        if (finding) return
        finding = true
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Discovery.all(ctx) }
                fun set(key: String, v: String) {
                    if (v.isNotBlank() && (force || prefs.get(key).isBlank())) prefs.put(key, v)
                }
                set(K.TV_IP, f.tvIp); set(K.TV_MAC, f.tvMac)
                set(K.PS5_IP, f.ps5Ip)
                set(K.PC_IP, f.pcIp)
                val parts = buildList {
                    if (f.tvIp.isNotBlank()) add("TV ${f.tvIp}")
                    if (f.ps5Ip.isNotBlank()) add("PS5 ${f.ps5Ip}")
                    if (f.pcIp.isNotBlank()) add("PC ${f.pcIp}")
                }
                msg = if (parts.isEmpty()) "Cihaz bulunamadı. Hepsi aynı Wi-Fi'de ve açık mı?" else "Bulundu: " + parts.joinToString(", ")
            } catch (e: Exception) {
                msg = "Arama başarısız: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                finding = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // İlk açılışta eksik IP'leri otomatik bul
        if (prefs.get(K.TV_IP).isBlank() || prefs.get(K.PC_IP).isBlank() || prefs.get(K.PS5_IP).isBlank()) discover(false)
        var tvDown = 0
        while (true) {
            val pcIp = prefs.get(K.PC_IP)
            val tvIp = prefs.get(K.TV_IP)
            pcUp = if (pcIp.isBlank()) null else withContext(Dispatchers.IO) { Net.up(pcIp, intArrayOf(22, 3389, 445, 135)) }
            tvUp = if (tvIp.isBlank()) null else withContext(Dispatchers.IO) { Net.up(tvIp, intArrayOf(6466, 8009, 8008)) }
            val psIp = prefs.get(K.PS5_IP)
            ps5Up = if (psIp.isBlank()) null else withContext(Dispatchers.IO) { Discovery.ps5Alive(psIp) }
            // TV'nin IP'si (DHCP) değiştiyse yeniden bul
            if (tvIp.isNotBlank() && tvUp == false) tvDown++ else tvDown = 0
            if (tvDown >= 4) {
                tvDown = 0
                val n = withContext(Dispatchers.IO) { Discovery.findTv(ctx) }
                if (n != null && n != tvIp) {
                    prefs.put(K.TV_IP, n)
                    msg = "TV'nin IP adresi değişmiş, güncellendi: $n"
                }
            }
            if (screen == "remote" && tvIp.isNotBlank() && tvUp == true) {
                withContext(Dispatchers.IO) {
                    try { tv.connect(tvIp) } catch (_: Exception) {}
                }
                nowApp = tv.currentApp
                tvOnState = tv.poweredOn
            }
            delay(4000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(theme.c1, theme.c2, theme.c3),
                        center = Offset(size.width * 0.5f, size.height * 0.62f),
                        radius = size.maxDimension * 0.95f,
                    ),
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (screen) {
                    "settings" -> SettingsScreen(prefs, theme, msg, finding, onFind = { discover(true) },
                        onBack = {
                            themeKey = prefs.get(K.THEME)
                            screen = "home"
                        },
                        onPair = {
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
                        },
                    )

                    "home" -> HomeScreen(
                        theme = theme, msg = msg, pcUp = pcUp, tvUp = tvUp, ps5Up = ps5Up,
                        onSettings = { screen = "settings" },
                        onTvPanel = {
                            val ip = prefs.get(K.TV_IP)
                            if (ip.isBlank()) {
                                msg = "Önce Ayarlar'dan TV IP adresini gir"
                            } else {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip:8765"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (e: Exception) {
                                    msg = "Tarayıcı açılamadı"
                                }
                            }
                        },
                        onPcOn = { act("PC açılıyor") { pcOn() } },
                        onPcOff = { act("PC kapatılıyor") { pcOff() } },
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

                    "calendar" -> CalendarScreen(theme, prefs)

                    else -> RemoteScreen(
                        theme = theme, msg = msg, tvUp = tvUp, tvOnState = tvOnState, nowApp = nowApp,
                        selected = selectedTile,
                        onPower = {
                            if (tvUp == true && tvOnState != false) act("TV kapatılıyor") { tvOff() }
                            else act("TV açılıyor") { tvOn() }
                        },
                        onKey = { k -> quiet { tv.key(prefs.get(K.TV_IP), k) } },
                        onTile = { t ->
                            selectedTile = t.id
                            if (t.id == "ps5") act("PS5 uyandırılıyor") { ps5On() }
                            else if (t.link != null) act("${t.label} açılıyor") { tv.openLink(prefs.get(K.TV_IP), t.link) }
                        },
                    )
                }
            }
            if (screen != "settings") {
                BottomTabs(screen, theme) { screen = it }
            }
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

// ---------------------------------------------------------------- alt sekmeler

@Composable
private fun BottomTabs(current: String, theme: Theme, onSelect: (String) -> Unit) {
    val land = isLandscape()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (land) 6.dp else 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().glass(RoundedCornerShape(50), 0.14f).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for ((id, label) in listOf("remote" to "📺 Kumanda", "calendar" to "📅 Takvim", "home" to "🏠 Ev")) {
                val sel = current == id
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onSelect(id) }
                        .padding(vertical = if (land) 8.dp else 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- kumanda

@Composable
private fun RemoteScreen(
    theme: Theme,
    msg: String,
    tvUp: Boolean?,
    tvOnState: Boolean?,
    nowApp: String?,
    selected: String,
    onPower: () -> Unit,
    onKey: (Int) -> Unit,
    onTile: (AppTile) -> Unit,
) {
    val connected = tvUp == true
    val land = isLandscape()
    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.glass(RoundedCornerShape(50), 0.14f).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) Good else Bad))
                Spacer(Modifier.width(10.dp))
                Text("Google TV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(if (connected) "bağlı" else "çevrimdışı", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).glass(CircleShape, 0.14f).clickable(onClick = onPower),
                contentAlignment = Alignment.Center,
            ) { Text("⏻", color = Color.White, fontSize = 20.sp) }
        }

    }
    val nowCard: @Composable () -> Unit = {
        // Şimdi TV'de
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 0.12f).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val name = if (!connected) "TV kapalı" else appName(nowApp)
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(theme.accent, theme.c2))),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("ŞİMDİ TV'DE", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !connected -> "Açmak için ⏻ düğmesine bas"
                        tvOnState == false -> "Ekran kapalı"
                        else -> nowApp ?: "Bağlanıyor…"
                    },
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1,
                )
            }
        }

    }
    val tileRow: @Composable () -> Unit = {
        // Uygulama kartları
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Tiles) { t ->
                val sel = selected == t.id
                Box(
                    Modifier
                        .size(width = 112.dp, height = 132.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(t.c1, t.c2)))
                        .border(if (sel) 2.dp else 1.dp, Color.White.copy(alpha = if (sel) 0.95f else 0.15f), RoundedCornerShape(24.dp))
                        .clickable { onTile(t) }
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart,
                ) { Text(t.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }

    }
    val dpad: @Composable () -> Unit = {
        // Yön tuşları
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundBtn("↩") { onKey(TvRemote.KEY_BACK) }
                RoundBtn("⌂") { onKey(TvRemote.KEY_HOME) }
            }
            Box(Modifier.size(214.dp).glass(CircleShape, 0.13f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(86.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                        .clickable { onKey(TvRemote.KEY_OK) },
                    contentAlignment = Alignment.Center,
                ) { Text("Tamam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Chev("▲", Alignment.TopCenter) { onKey(TvRemote.KEY_UP) }
                Chev("▼", Alignment.BottomCenter) { onKey(TvRemote.KEY_DOWN) }
                Chev("◀", Alignment.CenterStart) { onKey(TvRemote.KEY_LEFT) }
                Chev("▶", Alignment.CenterEnd) { onKey(TvRemote.KEY_RIGHT) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundBtn("⏯") { onKey(TvRemote.KEY_PLAY_PAUSE) }
                RoundBtn("🔇") { onKey(TvRemote.KEY_MUTE) }
            }
        }

    }
    val volume: @Composable () -> Unit = {
        // Ses
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(50), 0.13f).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldBtn("−") { onKey(TvRemote.KEY_VOL_DOWN) }
            Text("🔊  Ses", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            HoldBtn("+") { onKey(TvRemote.KEY_VOL_UP) }
        }

    }
    val msgLine: @Composable () -> Unit = {
        if (msg.isNotBlank()) {
            Text(msg, color = if (msg.contains("başarısız") || msg.contains("Hata")) Bad else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
    if (land) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header(); nowCard(); tileRow(); msgLine()
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                dpad(); volume()
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header(); nowCard(); tileRow(); dpad(); volume(); msgLine()
        }
    }
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).glass(CircleShape, 0.14f).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 20.sp) }
}

@Composable
private fun BoxScope.Chev(label: String, at: Alignment, onClick: () -> Unit) {
    Box(
        Modifier.align(at).padding(10.dp).size(52.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp) }
}

/** Basılı tutunca tekrarlayan düğme (ses için) */
@Composable
private fun HoldBtn(label: String, onTick: () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        onTick()
                        delay(400)
                        while (true) {
                            onTick()
                            delay(140)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

// ---------------------------------------------------------------- ev (cihazlar)

@Composable
private fun HomeScreen(
    theme: Theme,
    msg: String,
    pcUp: Boolean?,
    tvUp: Boolean?,
    ps5Up: Boolean?,
    onSettings: () -> Unit,
    onTvPanel: () -> Unit,
    onPcOn: () -> Unit,
    onPcOff: () -> Unit,
    onPsOn: () -> Unit,
    onGame: () -> Unit,
    onMovie: () -> Unit,
    onAllOff: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ev", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Evdeki her şey tek yerden", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
            }
            Box(Modifier.size(48.dp).glass(CircleShape, 0.14f).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                Text("⚙", color = Color.White, fontSize = 20.sp)
            }
        }

        Text("Sahneler", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Scene("🎮", "Oyun", onGame, Modifier.weight(1f))
            Scene("🎬", "Film", onMovie, Modifier.weight(1f))
            Scene("🌙", "Hepsini kapat", onAllOff, Modifier.weight(1f))
        }

        Text("Cihazlar", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val cards: List<@Composable () -> Unit> = listOf(
            { DeviceCard("🖥️", "Bilgisayar", "Wake-on-LAN ile aç, SSH ile kapat", pcUp, onPcOn, onPcOff) },
            { DeviceCard("🎮", "PlayStation 5", "Uyandır. Kapatma: TV ile (HDMI-CEC)", ps5Up, onPsOn, null) },
            { DeviceCard("📺", "TV arayüzü", "Afiş, video, ekran koruyucu, takvim", tvUp, onTvPanel, null, onLabel = "Paneli aç") },
        )
        if (isLandscape()) {
            // Yatayda iki sütun
            for (pair in cards.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (c in pair) Box(Modifier.weight(1f)) { c() }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            cards.forEach { it() }
        }

        if (msg.isNotBlank()) {
            Text(msg, color = if (msg.contains("başarısız") || msg.contains("başlatılamadı")) Bad else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun Scene(emoji: String, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.height(88.dp).glass(RoundedCornerShape(22.dp), 0.15f).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeviceCard(
    emoji: String,
    title: String,
    sub: String,
    up: Boolean?,
    onOn: () -> Unit,
    onOff: (() -> Unit)?,
    onLabel: String = "Aç",
) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 0.12f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            val dot = when (up) {
                true -> Good
                false -> Bad
                null -> Color.White.copy(alpha = 0.4f)
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(
                when (up) { true -> "açık"; false -> "kapalı"; null -> "—" },
                color = dot, fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOn, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
            ) { Text(onLabel) }
            if (onOff != null) {
                OutlinedButton(
                    onClick = onOff, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) { Text("Kapat", color = Color.White) }
            }
        }
    }
}

// ---------------------------------------------------------------- ayarlar

@Composable
private fun SettingsScreen(prefs: Prefs, theme: Theme, msg: String, finding: Boolean, onFind: () -> Unit, onBack: () -> Unit, onPair: () -> Unit) {
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
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    fun save() {
        K.all.forEach { prefs.put(it, s[it] ?: "") }
    }

    // Arama bitince bulunan değerleri alanlara yansıt
    LaunchedEffect(finding) {
        if (!finding) {
            for (k in listOf(K.TV_IP, K.TV_MAC, K.PS5_IP, K.PC_IP)) {
                val v = prefs.get(k)
                if (v.isNotBlank()) s[k] = v
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = { save(); onBack() }) { Text("‹ Kaydet ve dön", color = Color.White) }
        Text("Ayarlar", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)

        Text("🎨 Tema", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (t in Themes) {
                val sel = s[K.THEME] == t.key
                Box(
                    Modifier
                        .size(width = 74.dp, height = 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(t.c1, t.c2)))
                        .border(if (sel) 2.dp else 1.dp, Color.White.copy(alpha = if (sel) 1f else 0.25f), RoundedCornerShape(14.dp))
                        .clickable { s[K.THEME] = t.key },
                    contentAlignment = Alignment.Center,
                ) { Text(t.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Button(
            onClick = { save(); onFind() }, enabled = !finding, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color.White),
        ) { Text(if (finding) "Ağ taranıyor…" else "🔍 Ağdaki cihazları otomatik bul") }
        Text(
            "TV, PS5 ve bilgisayarı aynı Wi-Fi'de kendisi bulur; IP'leri elle girmen gerekmez. PC'nin MAC adresi " +
                "Android tarafından okunamaz, bir kez Windows'ta 'ipconfig /all' ile bakıp gir.",
            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
        )

        Text("📺 Google TV", color = Color.White, fontWeight = FontWeight.SemiBold)
        Field("TV IP adresi (ör. 192.168.1.20)", K.TV_IP)
        Field("TV MAC adresi (Wake-on-LAN için)", K.TV_MAC)
        Button(
            onClick = { save(); onPair() }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
        ) { Text("TV'yi eşleştir (bir kez)") }
        Text(
            "TV'de: Ayarlar › Sistem › Ağ'da 'Wi-Fi/LAN ile uyandırma' ve ağ bekleme modunu aç. " +
                "Eşleştirmede TV ekranında 6 haneli kod çıkar.",
            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
        )

        Text("🖥️ Bilgisayar", color = Color.White, fontWeight = FontWeight.SemiBold)
        Field("PC IP adresi", K.PC_IP)
        Field("PC MAC adresi", K.PC_MAC)
        Field("SSH kullanıcı adı", K.PC_USER)
        Field("SSH şifresi", K.PC_PASS, password = true)
        Field("Kapatma komutu", K.PC_CMD)
        Text(
            "Windows'ta kapatmak için 'OpenSSH Server' özelliğini kur. Linux'ta komut: sudo shutdown now",
            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
        )

        Text("🎮 PlayStation 5", color = Color.White, fontWeight = FontWeight.SemiBold)
        Field("PS5 IP adresi", K.PS5_IP)
        Field("PS5 user-credential", K.PS5_CRED)
        Text(
            "PS5'te: Ayarlar › Sistem › Uzaktan Oynatma'yı aç; Güç tasarrufu › Dinlenme modunda kullanılabilir özellikler › " +
                "'İnternete bağlı kal' ve 'Ağdan PS5'i açmayı etkinleştir'. Kapatmak için Ayarlar › Sistem › HDMI › " +
                "'HDMI cihaz bağlantısını etkinleştir' aç, TV kapanınca PS5 de dinlenmeye geçer.",
            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
        )

        Button(
            onClick = { save(); onBack() }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color.White),
        ) { Text("Kaydet") }
        Text(msg, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- takvim

private data class CalItem(val id: String, val title: String, val date: String, val time: String)

/** TV'deki Ev TV uygulamasının takvim uçları (port 8765) */
private object TvCal {
    private fun conn(ip: String, path: String, method: String): HttpURLConnection {
        val c = URL("http://$ip:8765$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 3000
        c.readTimeout = 5000
        return c
    }

    fun list(ip: String): List<CalItem> {
        val t = conn(ip, "/api/events", "GET").inputStream.bufferedReader().readText()
        val a = JSONArray(t)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            CalItem(o.optString("id"), o.optString("title"), o.optString("date"), o.optString("time"))
        }
    }

    fun add(ip: String, e: CalItem) {
        val c = conn(ip, "/api/events/add", "POST")
        c.doOutput = true
        val body = JSONObject().put("id", e.id).put("title", e.title).put("date", e.date).put("time", e.time).toString().toByteArray(Charsets.UTF_8)
        c.setFixedLengthStreamingMode(body.size)
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        if (code != 200) throw Exception(c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
    }

    fun delete(ip: String, id: String) {
        val c = conn(ip, "/api/events/delete?id=" + URLEncoder.encode(id, "UTF-8"), "POST")
        c.doOutput = true
        c.setFixedLengthStreamingMode(0)
        c.outputStream.close()
        c.responseCode
    }
}

private fun daysLeft(date: String): Int? = try {
    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date)).toInt()
} catch (_: Exception) {
    null
}

private fun whenLabel(n: Int?): String = when {
    n == null -> ""
    n < 0 -> "Geçti"
    n == 0 -> "Bugün"
    n == 1 -> "Yarın"
    else -> "$n gün kaldı"
}

@Composable
private fun CalendarScreen(theme: Theme, prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<CalItem>() }
    var info by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var time by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ip = prefs.get(K.TV_IP)

    fun refresh() {
        if (ip.isBlank()) {
            info = "Önce Ev › ⚙ Ayarlar'dan TV'yi bul (Otomatik bul)."
            return
        }
        scope.launch {
            try {
                val l = withContext(Dispatchers.IO) { TvCal.list(ip) }
                items.clear()
                items.addAll(l.filter { (daysLeft(it.date) ?: 0) >= 0 }.sortedBy { it.date + it.time })
                info = ""
            } catch (e: Exception) {
                info = "TV'ye ulaşılamadı. TV açık ve Ev TV ana ekranı çalışıyor mu?"
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun pickDate() {
        val d = try { LocalDate.parse(date) } catch (_: Exception) { LocalDate.now() }
        android.app.DatePickerDialog(ctx, { _, y, m, day ->
            date = LocalDate.of(y, m + 1, day).toString()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun pickTime() {
        android.app.TimePickerDialog(ctx, { _, h, m ->
            time = String.format(Locale.US, "%02d:%02d", h, m)
        }, 20, 0, true).show()
    }

    val land = isLandscape()
    val pad = if (land) 12.dp else 18.dp

    @Composable
    fun Chip(label: String, sel: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (sel) theme.accent else Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }

    val form: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 0.12f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Yeni etkinlik", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text("Ne var? (ör. Doktor, Doğum günü)") }, modifier = Modifier.fillMaxWidth(),
            )
            val today = LocalDate.now()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Bugün", date == today.toString()) { date = today.toString() }
                Chip("Yarın", date == today.plusDays(1).toString()) { date = today.plusDays(1).toString() }
                Chip("1 hafta", date == today.plusDays(7).toString()) { date = today.plusDays(7).toString() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dl = try { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))) } catch (_: Exception) { date }
                Chip("📅 $dl", false) { pickDate() }
                Chip(if (time.isBlank()) "🕒 Saat ekle" else "🕒 $time", false) { pickTime() }
                if (time.isNotBlank()) Chip("✕", false) { time = "" }
            }
            Button(
                onClick = {
                    if (title.isBlank()) { info = "Bir başlık yaz"; return@Button }
                    if (ip.isBlank()) { refresh(); return@Button }
                    busy = true
                    val e = CalItem(System.currentTimeMillis().toString(), title.trim(), date, time)
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { TvCal.add(ip, e) }
                            title = ""
                            time = ""
                            info = "TV'ye eklendi ✓"
                            refresh()
                        } catch (ex: Exception) {
                            info = "Eklenemedi: ${ex.message ?: "TV'ye ulaşılamadı"}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color.White),
            ) { Text(if (busy) "Ekleniyor…" else "TV'ye ekle") }
            if (info.isNotBlank()) Text(info, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }

    val list: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yaklaşanlar", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("↻ Yenile", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.clickable { refresh() }.padding(6.dp))
            }
            if (items.isEmpty()) {
                Text("Henüz etkinlik yok. Eklediklerin TV ana ekranında görünür.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            for (e in items) {
                val n = daysLeft(e.date)
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), 0.10f).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(74.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (n == 0) theme.accent else Color.White.copy(alpha = 0.14f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (n) { 0 -> "Bugün"; 1 -> "Yarın"; null -> "?"; else -> "$n gün" },
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                        Text(
                            e.date + (if (e.time.isNotBlank()) "  ${e.time}" else "") + "  ·  " + whenLabel(n),
                            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
                        )
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { TvCal.delete(ip, e.id) }
                                    items.remove(e)
                                } catch (_: Exception) {
                                    info = "Silinemedi"
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp) }
                }
            }
        }
    }

    if (land) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = pad), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Takvim", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                form()
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { list() }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = pad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Takvim", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Eklediğin etkinlikler TV ana ekranında \"Bugün\", \"3 gün kaldı\" diye görünür.", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
            form()
            list()
        }
    }
}
