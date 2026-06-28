package com.kotlin.card.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Render the masked card as a shareable PNG using the same Vault palette as the
 * on-screen hero: navy gradient, gold chip, brand wordmark, and a mono number
 * whose kept (revealed) digits glow mint while masked glyphs stay muted.
 */
fun renderCardBitmap(maskedDigits: String, brand: String): Bitmap {
    val w = 1100
    val h = 693 // ~1.586:1 card ratio
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val face = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF1E2A4A.toInt(), 0xFF3A2C6B.toInt(), 0xFF0C1430.toInt()),
            null, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRoundRect(face, 56f, 56f, bg)

    // EMV chip
    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8C77E.toInt() }
    canvas.drawRoundRect(RectF(90f, 110f, 230f, 210f), 18f, 18f, chip)

    // Brand wordmark, top-right
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4F6FF.toInt()
        textSize = 58f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(brand, w - 90f, 180f, brandPaint)

    // Masked number, grouped into 4s; kept digits mint, masked muted
    val mono = Typeface.MONOSPACE
    val keptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF34E0A1.toInt()
        textSize = 86f
        typeface = Typeface.create(mono, Typeface.BOLD)
    }
    val maskedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8893B2.toInt()
        textSize = 86f
        typeface = mono
    }
    val charW = keptPaint.measureText("0")
    var x = 90f
    val y = 470f
    maskedDigits.forEachIndexed { index, c ->
        if (index != 0 && index % 4 == 0) x += charW * 0.6f
        canvas.drawText(c.toString(), x, y, if (c.isDigit()) keptPaint else maskedPaint)
        x += charW
    }

    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8893B2.toInt()
        textSize = 38f
    }
    canvas.drawText("Masked on device · Card Pro", 90f, h - 80f, footer)

    return bitmap
}

/** Write [bitmap] to cache and fire a share-sheet for the PNG. */
fun shareCardImage(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "card.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, null))
}
