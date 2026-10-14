package com.openscad.viewer.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.openscad.viewer.render.RenderMesh
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary STL export for the current preview mesh.
 *
 * The mesh is already a flat triangle soup in millimetres, so it maps directly
 * onto the STL record layout. Binary (not ASCII) keeps a 40k-triangle part to a
 * few hundred kilobytes, which matters when handing it to another app.
 */
object StlExport {

    /** `lotus_pod_lamp_screen-screen.stl`, or `model.stl` when unnamed. */
    fun fileName(sourceName: String, part: String?): String {
        val base = sourceName.substringBeforeLast('.').ifBlank { "model" }
        val suffix = part?.trim()?.trim('"')?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
        return "$base$suffix.stl"
    }

    fun writeBinaryStl(mesh: RenderMesh, out: OutputStream) {
        val header = ByteArray(80)
        val stamp = "OpenSCAD Viewer binary STL".toByteArray()
        System.arraycopy(stamp, 0, header, 0, minOf(stamp.size, header.size))
        out.write(header)

        val count = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(mesh.triangles.size).array()
        out.write(count)

        val record = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
        for (t in mesh.triangles) {
            record.clear()
            record.putFloat(t.normal.x.toFloat()).putFloat(t.normal.y.toFloat()).putFloat(t.normal.z.toFloat())
            for (v in listOf(t.a, t.b, t.c)) {
                record.putFloat(v.x.toFloat()).putFloat(v.y.toFloat()).putFloat(v.z.toFloat())
            }
            record.putShort(0)
            out.write(record.array(), 0, 50)
        }
    }

    /** Writes to the public Downloads collection, where a file manager can find it. */
    fun exportToDownloads(context: Context, mesh: RenderMesh, fileName: String): Uri {
        val resolver = context.contentResolver
        val relative = Environment.DIRECTORY_DOWNLOADS + "/OpenSCAD Viewer"
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName),
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_STL)
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create $fileName")
        resolver.openOutputStream(uri)?.use { writeBinaryStl(mesh, it) }
            ?: error("Could not write $fileName")
        return uri
    }

    /** Writes into the app's external files and returns a shareable provider Uri. */
    fun writeForSharing(context: Context, mesh: RenderMesh, fileName: String): Uri {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { writeBinaryStl(mesh, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Any app that accepts an STL gets offered. The registered STL type is used
     * when something resolves it, otherwise the wildcard type is the fallback so
     * a slicer that only declares a wildcard still shows up.
     */
    fun shareIntent(context: Context, uri: Uri, fileName: String): Intent {
        val type = if (context.packageManager.queryIntentActivities(base(context, uri, fileName, MIME_STL), 0).isNotEmpty()) {
            MIME_STL
        } else {
            "*/*"
        }
        return base(context, uri, fileName, type)
    }

    private fun base(context: Context, uri: Uri, fileName: String, type: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    const val MIME_STL = "model/stl"
}
