package com.evkumanda.app.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Takvim etkinliği. date: yyyy-MM-dd, time: "HH:mm" veya boş */
data class CalEvent(val id: String, val title: String, val date: String, val time: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("date", date).put("time", time)

    companion object {
        fun fromJson(o: JSONObject) = CalEvent(
            o.optString("id").ifBlank { System.nanoTime().toString() },
            o.optString("title"), o.optString("date"), o.optString("time"),
        )
    }
}

data class Hero(val image: String, val title: String, val subtitle: String, val pkg: String)

/** Tüm TV arayüzü ayarları. Web panelinden düzenlenir, config.json olarak saklanır. */
data class TvConfig(
    val heroes: List<Hero> = emptyList(),
    val pinned: List<String> = emptyList(),
    val saverMode: String = "aurora", // aurora | stars | snow | images | video
    val saverInterval: Int = 12,
    val saverClock: Boolean = true,
    val idleMin: Int = 5,
    val city: String = "İstanbul",
    val lat: Double = 41.01,
    val lon: Double = 28.97,
    val cdTitle: String = "",
    val cdDate: String = "", // yyyy-MM-dd
    val pcIp: String = "",
    val pcMac: String = "",
    val ps5Ip: String = "",
    val ps5Cred: String = "",
    val userName: String = "Carry",
    val accent: String = "#8B6CFF",
    val saverVideo: String = "",
    val homeVideo: String = "",
    val events: List<CalEvent> = emptyList(),
    val usbAuto: Boolean = true,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        val hs = JSONArray()
        heroes.forEach {
            hs.put(
                JSONObject().put("image", it.image).put("title", it.title)
                    .put("subtitle", it.subtitle).put("pkg", it.pkg),
            )
        }
        o.put("heroes", hs)
        o.put("pinned", JSONArray(pinned))
        o.put("saverMode", saverMode)
        o.put("saverInterval", saverInterval)
        o.put("saverClock", saverClock)
        o.put("idleMin", idleMin)
        o.put("city", city)
        o.put("lat", lat)
        o.put("lon", lon)
        o.put("cdTitle", cdTitle)
        o.put("cdDate", cdDate)
        o.put("pcIp", pcIp)
        o.put("pcMac", pcMac)
        o.put("ps5Ip", ps5Ip)
        o.put("ps5Cred", ps5Cred)
        o.put("userName", userName)
        o.put("accent", accent)
        o.put("saverVideo", saverVideo)
        o.put("homeVideo", homeVideo)
        o.put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
        o.put("usbAuto", usbAuto)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): TvConfig {
            val d = TvConfig()
            val heroes = ArrayList<Hero>()
            val ha = o.optJSONArray("heroes")
            if (ha != null) {
                for (i in 0 until ha.length()) {
                    val h = ha.optJSONObject(i) ?: continue
                    heroes.add(Hero(h.optString("image"), h.optString("title"), h.optString("subtitle"), h.optString("pkg")))
                }
            }
            val pinned = ArrayList<String>()
            val pa = o.optJSONArray("pinned")
            if (pa != null) for (i in 0 until pa.length()) pinned.add(pa.optString(i))
            val events = ArrayList<CalEvent>()
            val ea = o.optJSONArray("events")
            if (ea != null) for (i in 0 until ea.length()) ea.optJSONObject(i)?.let { e -> CalEvent.fromJson(e).takeIf { it.title.isNotBlank() && it.date.isNotBlank() }?.let { events.add(it) } }
            return TvConfig(
                heroes = heroes,
                pinned = pinned,
                saverMode = o.optString("saverMode", d.saverMode),
                saverInterval = o.optInt("saverInterval", d.saverInterval).coerceIn(4, 120),
                saverClock = o.optBoolean("saverClock", d.saverClock),
                idleMin = o.optInt("idleMin", d.idleMin).coerceIn(0, 120),
                city = o.optString("city", d.city),
                lat = o.optDouble("lat", d.lat),
                lon = o.optDouble("lon", d.lon),
                cdTitle = o.optString("cdTitle"),
                cdDate = o.optString("cdDate"),
                pcIp = o.optString("pcIp"),
                pcMac = o.optString("pcMac"),
                ps5Ip = o.optString("ps5Ip"),
                ps5Cred = o.optString("ps5Cred"),
                userName = o.optString("userName").ifBlank { d.userName },
                accent = o.optString("accent", d.accent),
                saverVideo = o.optString("saverVideo"),
                homeVideo = o.optString("homeVideo"),
                events = events,
                usbAuto = o.optBoolean("usbAuto", d.usbAuto),
            )
        }
    }
}

object TvStore {
    var cfg by mutableStateOf(TvConfig())
        private set

    fun mediaDir(ctx: Context): File = File(ctx.filesDir, "media").apply { mkdirs() }

    private fun file(ctx: Context) = File(ctx.filesDir, "config.json")

    fun load(ctx: Context): TvConfig {
        val f = file(ctx)
        if (f.exists()) {
            try {
                cfg = TvConfig.fromJson(JSONObject(f.readText()))
            } catch (_: Exception) {
            }
        }
        return cfg
    }

    fun save(ctx: Context, c: TvConfig) {
        file(ctx).writeText(c.toJson().toString())
        cfg = c
    }

    fun mediaFiles(ctx: Context, prefix: String): List<File> =
        (mediaDir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && it.name.startsWith(prefix) }.sortedBy { it.name }
}
