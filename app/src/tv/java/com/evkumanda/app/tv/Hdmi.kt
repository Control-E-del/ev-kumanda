package com.evkumanda.app.tv

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

data class HdmiInput(val id: String, val label: String)

/** TV'ye takılı (bağlı) HDMI girişleri. Apple TV, konsol vb. */
fun hdmiInputs(ctx: Context): List<HdmiInput> {
    val tm = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    return try {
        val all = tm.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
        // Bir porta CEC ile bağlı cihaz (ör. "Apple TV") varsa portun kendisini gizle
        val parents = all.mapNotNull { it.parentId }.toSet()
        all.filter {
            it.id !in parents && !it.isHidden(ctx) &&
                tm.getInputState(it.id) != TvInputManager.INPUT_STATE_DISCONNECTED
        }.map {
            val custom = it.loadCustomLabel(ctx)?.toString()
            val label = if (!custom.isNullOrBlank()) custom else (it.loadLabel(ctx)?.toString() ?: "HDMI")
            HdmiInput(it.id, label)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun openHdmi(ctx: Context, id: String): Boolean = try {
    val i = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
    true
} catch (_: Exception) {
    false
}
