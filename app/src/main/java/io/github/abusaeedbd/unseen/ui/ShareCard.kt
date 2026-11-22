package io.github.abusaeedbd.unseen.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.data.formatBytes
import java.io.File

/** Draws a 1080×1350 "my week with Unseen" image and opens the share sheet. */
object ShareCard {
    private const val LINK = "github.com/Abusaeed-bd/Unseen"

    fun share(context: Context) {
        val file = draw(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Unseen blocks the trackers hidden inside my apps. Get it: https://$LINK")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your week").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun draw(context: Context): File {
        val week = Stats.week()
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF12152A.toInt())

        val accent = 0xFF8B6CFF.toInt()
        val white = 0xFFFFFFFF.toInt()
        val muted = 0xFFA3A7C2.toInt()
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT) {
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                this.typeface = typeface
            })
        }

        // Logo: purple circle with the white ghost
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        c.drawCircle(160f, 170f, 80f, circle)
        ContextCompat.getDrawable(context, R.drawable.ic_stat_unseen)?.let { ghost ->
            ghost.setBounds(110, 120, 210, 220)
            ghost.draw(c)
        }
        text("Unseen", 270f, 190f, 64f, white, bold)
        text("My week", 80f, 360f, 44f, muted)

        text("%,d".format(week.counts.total), 80f, 520f, 170f, white, bold)
        text("trackers and ads blocked", 80f, 590f, 48f, muted)

        val rows = listOf(
            "Trackers" to "%,d".format(week.counts.trackers),
            "Ads" to "%,d".format(week.counts.ads),
            "Dangerous sites" to "%,d".format(week.counts.scams),
            "Companies kept out" to "${week.companies}",
            "Data saved (about)" to formatBytes(week.counts.dataSavedBytes),
        )
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2140.toInt() }
        c.drawRoundRect(RectF(60f, 650f, w - 60f, 650f + rows.size * 84f + 40f), 36f, 36f, card)
        rows.forEachIndexed { i, (label, value) ->
            val y = 720f + i * 84f
            text(label, 100f, y, 40f, muted)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 40f; color = white; typeface = bold; textAlign = Paint.Align.RIGHT
            }
            c.drawText(value, w - 100f, y, p)
        }

        week.topApps.firstOrNull()?.let { (pkg, n) ->
            text("Most eager to track me:", 80f, 1150f, 38f, muted)
            text("${AppNames.label(context, pkg)} ($n×)", 80f, 1205f, 44f, white, bold)
        }
        text("Apps can't watch what they can't see.", 80f, 1265f, 32f, accent)
        text(LINK, 80f, 1310f, 32f, muted)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "unseen-week.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }
}
