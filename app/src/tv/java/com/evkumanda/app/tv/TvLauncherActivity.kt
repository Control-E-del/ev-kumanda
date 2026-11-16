package com.evkumanda.app.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.evkumanda.app.Ps5
import com.evkumanda.app.Wol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sin

class TvLauncherActivity : ComponentActivity() {
    @Volatile
    var lastInput: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvStore.load(applicationContext)
        WebPanel.start(applicationContext)
        setContent { LauncherRoot(this) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInput = System.currentTimeMillis()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        lastInput = System.currentTimeMillis()
    }
}

// ---------------------------------------------------------------- yardımcılar

object Weather {
    var text by mutableStateOf("")
    var emoji by mutableStateOf("")
    var temp by mutableStateOf("")

    private fun emojiFor(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌧️"
        else -> if (code >= 95) "⛈️" else "🌡️"
    }

    fun fetch(lat: Double, lon: Double) {
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val s = c.inputStream.bufferedReader().readText()
            val cur = JSONObject(s).getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m")).toInt()
            val e = emojiFor(cur.getInt("weather_code"))
            emoji = e
            temp = "$t°"
            text = "$e $t°"
        } catch (_: Exception) {
        }
    }
}

private fun toast(ctx: Context, msg: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}

fun launchPkg(ctx: Context, pkg: String): Boolean {
    val pm = ctx.packageManager
    val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
    return true
}

private fun openMarket(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(ctx, "Mağaza açılamadı")
        }
    }
}

private fun openFiles(ctx: Context) {
    val cands = listOf(
        "com.google.android.documentsui", "com.android.documentsui", "com.lonelycatgames.Xplore",
        "nextapp.fx", "pl.solidexplorer2", "com.mixplorer", "com.cxinventor.file.explorer",
    )
    for (p in cands) if (launchPkg(ctx, p)) return
    toast(ctx, "Dosya yöneticisi yok, X-plore için mağaza açılıyor")
    openMarket(ctx, "com.lonelycatgames.Xplore")
}

private fun openSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        toast(ctx, "Ayarlar açılamadı")
    }
}

private val defaultPinned = listOf(
    "com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android",
    "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "tv.twitch.android.app",
)

@Composable
fun rememberAppIcon(ctx: Context, pkg: String): ImageBitmap? {
    val ic by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(256, 256).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    return ic
}

private fun greeting(name: String): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val g = when {
        h < 5 -> "İyi geceler"
        h < 12 -> "Günaydın"
        h < 18 -> "İyi günler"
        else -> "İyi akşamlar"
    }
    return if (name.isBlank()) g else "$g $name"
}

// ---------------------------------------------------------------- kök

@Composable
fun LauncherRoot(act: TvLauncherActivity) {
    val ctx = act.applicationContext
    var screen by remember { mutableStateOf("home") }
    val cfg = TvStore.cfg
    val accent = remember(cfg.accent) { Color(parseAccent(cfg.accent)) }

    BackHandler(enabled = true) { if (screen != "home") screen = "home" }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val c = TvStore.cfg
            if (c.idleMin > 0 && screen == "home" &&
                System.currentTimeMillis() - act.lastInput > c.idleMin * 60_000L
            ) {
                act.lastInput = System.currentTimeMillis()
                act.startActivity(Intent(act, SaverActivity::class.java))
            }
        }
    }

    LaunchedEffect(cfg.lat, cfg.lon) {
        while (true) {
            withContext(Dispatchers.IO) { Weather.fetch(cfg.lat, cfg.lon) }
            delay(30 * 60 * 1000L)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
        Crossfade(targetState = screen, animationSpec = tween(400), label = "screen") { s ->
            if (s == "apps") {
                AllAppsScreen(ctx, accent)
            } else {
                HomeScreen(act, ctx, cfg, accent, onAllApps = { screen = "apps" })
            }
        }
    }
}

// ---------------------------------------------------------------- ana ekran

private class Entry(
    val label: String,
    val pkg: String?,
    val emoji: String?,
    val tint: Color,
    val action: () -> Unit,
)

@Composable
fun HomeScreen(act: ComponentActivity, ctx: Context, cfg: TvConfig, accent: Color, onAllApps: () -> Unit) {
    val heroes = remember(cfg.heroes) { cfg.heroes.filter { it.image.isNotBlank() } }
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(heroes.size) {
        idx = 0
        while (heroes.size > 1) {
            delay(9000)
            idx = (idx + 1) % heroes.size
        }
    }
    val inf = rememberInfiniteTransition(label = "zoom")
    val zoom by inf.animateFloat(
        1f, 1.07f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "z",
    )

    var hdmi by remember { mutableStateOf(emptyList<HdmiInput>()) }
    LaunchedEffect(Unit) {
        while (true) {
            hdmi = withContext(Dispatchers.IO) { hdmiInputs(ctx) }
            delay(5000)
        }
    }

    val hero = heroes.getOrNull(idx)
    val dateFmt = remember { SimpleDateFormat("d MMMM EEEE", Locale("tr")) }
    val title = hero?.title?.ifBlank { null } ?: if (heroes.isEmpty()) greeting(cfg.userName) else ""
    val subtitle = hero?.subtitle?.ifBlank { null } ?: if (heroes.isEmpty()) dateFmt.format(Date()) else ""

    Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
        if (cfg.homeVideo.isNotBlank() && File(TvStore.mediaDir(ctx), cfg.homeVideo).exists()) {
            HomeVideo(act, cfg.homeVideo)
        } else if (heroes.isEmpty()) {
            AnimatedGradient(accent)
        } else {
            Crossfade(targetState = idx.coerceIn(0, heroes.size - 1), animationSpec = tween(1400), label = "hero") { i ->
                HeroImage(ctx, heroes[i].image, zoom)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6000000), Color(0x55000000), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF2000000))))

        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Entrance(0) {
                    Text("Ev TV", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Crossfade(targetState = Pair(title, subtitle), animationSpec = tween(700), label = "herotext") { ts ->
                    val tt = ts.first
                    val st = ts.second
                    Column {
                        Text(tt, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (st.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(st, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                if (hero != null && hero.pkg.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Pill("▶  Aç", accent) { if (!launchPkg(ctx, hero.pkg)) toast(ctx, "Uygulama yüklü değil") }
                }
                Spacer(Modifier.height(26.dp))
                AppRow(ctx, cfg, hdmi, accent, onAllApps)
            }
            Spacer(Modifier.width(36.dp))
            Column(
                Modifier.width(310.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Entrance(100) { ClockCard(cfg) }
                Entrance(220) { WeatherCard(cfg) }
                Entrance(340) { CountdownCard(cfg, accent) }
                Entrance(460) { ControlCard(ctx, cfg, accent) }
            }
        }
    }
}

@Composable
private fun AppRow(ctx: Context, cfg: TvConfig, hdmi: List<HdmiInput>, accent: Color, onAllApps: () -> Unit) {
    val pm = ctx.packageManager
    val entries = remember(cfg.pinned, hdmi) {
        val list = ArrayList<Entry>()
        list.add(Entry("Play Store", "com.android.vending", "▶", Color(0xFF1E88E5)) {
            if (!launchPkg(ctx, "com.android.vending")) openMarket(ctx, "com.android.vending")
        })
        val pins = if (cfg.pinned.isNotEmpty()) cfg.pinned else defaultPinned
        for (p in pins) {
            if (p == "com.android.vending") continue
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
            } catch (_: Exception) {
                null
            }
            if (label != null && (pm.getLaunchIntentForPackage(p) != null || pm.getLeanbackLaunchIntentForPackage(p) != null)) {
                list.add(Entry(label, p, null, Color(0xFF3A3F5C)) { launchPkg(ctx, p) })
            }
        }
        for (h in hdmi) {
            list.add(Entry(h.label, null, "🔌", Color(0xFF2E7D6B)) {
                if (!openHdmi(ctx, h.id)) toast(ctx, "Giriş açılamadı")
            })
        }
        list.add(Entry("Dosyalar", null, "📁", Color(0xFFFFA726)) { openFiles(ctx) })
        list.add(Entry("Ayarlar", null, "⚙️", Color(0xFF607D8B)) { openSettings(ctx) })
        list.add(Entry("Tüm uygulamalar", null, "⊞", Color(0xFF7E57C2)) { onAllApps() })
        list
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(600)
        try { fr.requestFocus() } catch (_: Exception) {}
    }
    Entrance(200) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp, horizontal = 8.dp)) {
            items(entries.size) { i ->
                val e = entries[i]
                val icon = if (e.pkg != null) rememberAppIcon(ctx, e.pkg) else null
                Tile(
                    label = e.label, icon = icon, emoji = e.emoji, tint = e.tint, accent = accent,
                    modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                    onClick = e.action,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- görsel parçalar

@Composable
private fun HomeVideo(act: ComponentActivity, name: String) {
    val f = remember(name) { File(TvStore.mediaDir(act), name) }
    val holder = remember { arrayOfNulls<SeamlessVideoView>(1) }
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) holder[0]?.pause()
            else if (ev == Lifecycle.Event.ON_START) holder[0]?.start()
        }
        act.lifecycle.addObserver(obs)
        onDispose { act.lifecycle.removeObserver(obs) }
    }
    AndroidView(
        factory = { c ->
            val v = SeamlessVideoView(c, f)
            holder[0] = v
            v.start()
            v
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.release() },
    )
}

@Composable
private fun HeroImage(ctx: Context, name: String, zoom: Float) {
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            decodeScaled(File(TvStore.mediaDir(ctx), name), 1920)?.asImageBitmap()
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
    }
}

@Composable
private fun AnimatedGradient(accent: Color) {
    val inf = rememberInfiniteTransition(label = "bg")
    val p by inf.animateFloat(
        0f, 6.28f,
        infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "p",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF05070D))
        val w = size.width
        val h = size.height
        val cols = listOf(accent, Color(0xFF2BD9C5), Color(0xFF3A7BFF), Color(0xFFFF5FA2))
        cols.forEachIndexed { i, c ->
            val cx = w * (0.5f + 0.38f * sin(p + i * 1.7f))
            val cy = h * (0.5f + 0.32f * sin(p * 0.8f + i * 2.1f))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = max(w, h) * 0.5f,
                ),
            )
        }
    }
}

@Composable
private fun Entrance(delayMs: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(800), label = "enter")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 50f }) { content() }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(20.dp),
    ) { content() }
}

@Composable
fun Tile(
    label: String,
    icon: ImageBitmap?,
    emoji: String?,
    tint: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 280f), label = "tile")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "glow")
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .width(124.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(124.dp)
                .shadow((26 * glow).dp, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.55f))))
                .border(2.dp, Color.White.copy(alpha = 0.10f + 0.80f * glow), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = label, modifier = Modifier.size(78.dp))
            } else {
                Text(emoji ?: "•", fontSize = 48.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label, color = Color.White.copy(alpha = 0.65f + 0.35f * glow), fontSize = 14.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Pill(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "pill")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) accent else Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.2f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- widget'lar

@Composable
private fun ClockCard(cfg: TvConfig) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("tr")) }
    val dateFmt = remember { SimpleDateFormat("d MMMM, EEEE", Locale("tr")) }
    GlassCard {
        Column {
            AnimatedContent(
                targetState = timeFmt.format(now),
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(400))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(400)))
                },
                label = "clock",
            ) { t ->
                Text(t, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin)
            }
            Text(dateFmt.format(now), color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(greeting(cfg.userName), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WeatherCard(cfg: TvConfig) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Weather.emoji.ifBlank { "🌡️" }, fontSize = 44.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Weather.temp.ifBlank { "…" }, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
                Text(cfg.city, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CountdownCard(cfg: TvConfig, accent: Color) {
    val days = remember(cfg.cdDate) {
        try {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(cfg.cdDate)).toInt()
        } catch (_: Exception) {
            -1
        }
    }
    if (cfg.cdTitle.isBlank() || days < 0) return
    val frac by animateFloatAsState(minOf(days, 100) / 100f, tween(1600), label = "ring")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val st = 9.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.15f), -90f, 360f, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
                    drawArc(accent, -90f, 360f * frac, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
                }
                Text("$days", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("gün kaldı", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Text(cfg.cdTitle, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ControlCard(ctx: Context, cfg: TvConfig, accent: Color) {
    fun run(label: String, block: () -> Unit) {
        thread {
            try {
                block()
                toast(ctx, "$label ✓")
            } catch (e: Exception) {
                toast(ctx, "$label başarısız: ${e.message}")
            }
        }
    }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ev kontrol", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBtn("🖥️", "PC", accent, Modifier.weight(1f)) { run("PC açılıyor") { Wol.send(cfg.pcMac, cfg.pcIp) } }
                MiniBtn("🎮", "PS5", accent, Modifier.weight(1f)) { run("PS5 uyandırılıyor") { Ps5.wake(cfg.ps5Ip, cfg.ps5Cred) } }
                MiniBtn("🕹️", "Oyun", accent, Modifier.weight(1f)) {
                    run("Oyun modu") {
                        Wol.send(cfg.pcMac, cfg.pcIp)
                        Ps5.wake(cfg.ps5Ip, cfg.ps5Cred)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBtn(emoji: String, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "mini")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) accent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- tüm uygulamalar

@Composable
fun AllAppsScreen(ctx: Context, accent: Color) {
    val apps = remember { installedApps(ctx) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F1A), Color(0xFF1A1033)))),
    ) {
        Column(Modifier.padding(horizontal = 48.dp, vertical = 36.dp)) {
            Text("Tüm uygulamalar", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(apps) { a ->
                    val icon = rememberAppIcon(ctx, a.pkg)
                    Tile(
                        label = a.label, icon = icon, emoji = "•", tint = Color(0xFF3A3F5C), accent = accent,
                        onClick = { launchPkg(ctx, a.pkg) },
                    )
                }
            }
        }
    }
}
