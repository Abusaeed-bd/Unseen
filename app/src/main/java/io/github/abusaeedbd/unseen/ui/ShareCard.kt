package io.github.abusaeedbd.unseen.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.data.formatBytes
import java.io.File

/** Draws the 1080×1350 "Weekly Privacy Card" (same look as the in-app preview) and opens the share sheet. */
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
        val w = 1080f
        val h = 1350f
        val pad = 66f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val white = 0xFFFFFFFF.toInt()
        val accent = 0xFF8B6CFF.toInt()
        val accentSoft = 0xFFA78BFA.toInt()
        val label2 = 0xFF8E8E93.toInt()
        val green = 0xFF30D158.toInt()
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val italic = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)

        // Background: dark gradient with a purple glow top-right and a green glow bottom-left
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w * 0.4f, h, 0xFF191924.toInt(), 0xFF09090D.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.85f, h * 0.15f, 720f, 0x5C8B6CFF, 0x008B6CFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.15f, h * 0.85f, 760f, 0x2430D158, 0x0030D158, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT, align: Paint.Align = Paint.Align.LEFT) {
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                this.typeface = typeface
                textAlign = align
            })
        }
        fun roundRect(r: RectF, radius: Float, fill: Int, stroke: Int? = null) {
            c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            if (stroke != null) {
                c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                })
            }
        }

        // Header: logo tile, name, "LAST 7 DAYS" pill
        roundRect(RectF(pad, pad, pad + 120f, pad + 120f), 36f, 0xFF12152A.toInt(), 0x26FFFFFF)
        ContextCompat.getDrawable(context, R.drawable.ic_brand_ghost)?.let { ghost ->
            ghost.setBounds((pad + 24).toInt(), (pad + 24).toInt(), (pad + 96).toInt(), (pad + 96).toInt())
            ghost.draw(c)
        }
        text("Unseen", pad + 150f, pad + 54f, 46f, white, bold)
        text("Weekly Report", pad + 150f, pad + 100f, 32f, accentSoft)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; typeface = bold; letterSpacing = 0.08f }
        val pillText = "LAST 7 DAYS"
        val pillW = pillPaint.measureText(pillText) + 48f
        roundRect(RectF(w - pad - pillW, pad + 32f, w - pad, pad + 86f), 27f, 0x1AFFFFFF)
        c.drawText(pillText, w - pad - pillW + 24f, pad + 69f, pillPaint.apply { color = 0xE6FFFFFF.toInt() })

        // The number
        text("%,d".format(week.counts.total), pad, 640f, 200f, white, bold)
        text("Trackers & ads blocked", pad, 720f, 54f, accentSoft, bold)
        text("Stopped silently on-device before leaving this phone.", pad, 780f, 34f, label2)

        // Two tiles
        val tileW = (w - pad * 2 - 36f) / 2
        val tops = 850f
        val tiles = listOf(
            Triple("${week.companies}", "Companies kept out", white),
            Triple("~${formatBytes(week.counts.dataSavedBytes)}", "Data saved", green),
        )
        tiles.forEachIndexed { i, (value, label, color) ->
            val left = pad + i * (tileW + 36f)
            roundRect(RectF(left, tops, left + tileW, tops + 190f), 48f, 0x0FFFFFFF, 0x1AFFFFFF)
            text(value, left + 36f, tops + 92f, 64f, color, bold)
            text(label, left + 36f, tops + 148f, 34f, label2)
        }

        // Footer
        c.drawRect(pad, 1190f, w - pad, 1192f, Paint().apply { color = 0x1AFFFFFF })
        text("“Apps can't watch what they can't see.”", pad, 1260f, 32f, 0xBFFFFFFF.toInt(), italic)
        text(LINK, pad, 1305f, 28f, label2)
        text("v${appVersion(context)}", w - pad, 1260f, 30f, accentSoft, Typeface.MONOSPACE, Paint.Align.RIGHT)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "unseen-week.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
