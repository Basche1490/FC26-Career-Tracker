package com.fctracker.career.ui.recap

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Saving the recap pages as images or a PDF, and handing them to another app. Files live in the app's cache only. */
object RecapShare {
    private fun dir(context: Context): File = File(context.cacheDir, "shared/recap").apply { mkdirs() }

    fun authority(context: Context) = "${context.packageName}.files"

    /** Writes one page as a PNG and returns a link other apps can read (only while sharing). */
    fun savePng(context: Context, bmp: Bitmap, name: String): Uri {
        val f = File(dir(context), name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, authority(context), f)
    }

    /** Clears the images of an earlier share, so the cache does not grow. */
    fun clear(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }

    fun shareImages(context: Context, uris: List<Uri>, title: String) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.putExtra(Intent.EXTRA_TITLE, title)
        intent.clipData = ClipData.newRawUri(title, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** One page per image, each the size of the image in points halved (540 × 675 for a 1080 × 1350 page). */
    fun writePdf(pages: List<Bitmap>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, bmp ->
                val w = bmp.width / 2
                val h = bmp.height / 2
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
