package com.adf.pirobinetterie.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Partage de fichiers de l'app avec d'autres applications (appareil photo, lecteur PDF,
 * installeur de mise à jour) via une adresse content:// — équivalent minimal du FileProvider.
 */
class FichiersProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun fichier(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val segments = uri.pathSegments
        if (segments.size < 2) throw FileNotFoundException(uri.toString())
        val racine = racines(ctx)[segments[0]] ?: throw FileNotFoundException(uri.toString())
        val f = File(racine, segments.drop(1).joinToString("/"))
        if (!f.canonicalPath.startsWith(racine.canonicalPath + File.separator)) throw FileNotFoundException(uri.toString())
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fichier(uri)
        f.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = fichier(uri)
        val colonnes = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val valeurs = colonnes.map { c ->
            when (c) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(colonnes).apply { addRow(valeurs.toTypedArray()) }
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment.orEmpty().substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "apk" -> "application/vnd.android.package-archive"
        "xlsm" -> "application/vnd.ms-excel.sheet.macroEnabled.12"
        else -> "application/octet-stream"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private fun racines(ctx: Context): Map<String, File> = mapOf(
            "photos" to Depot.dossierPhotos(ctx),
            "pdf" to Depot.dossierPdf(ctx),
            "maj" to File(ctx.filesDir, "maj")
        )

        /** Adresse content:// d'un fichier situé sous l'une des racines partagées. */
        fun uri(ctx: Context, f: File): Uri {
            for ((nom, racine) in racines(ctx)) {
                val base = racine.canonicalPath + File.separator
                val chemin = f.canonicalPath
                if (chemin.startsWith(base)) {
                    return Uri.Builder().scheme("content").authority(ctx.packageName + ".fichiers")
                        .appendPath(nom).apply { chemin.removePrefix(base).split('/').forEach { appendPath(it) } }
                        .build()
                }
            }
            throw IllegalArgumentException("Fichier non partageable : $f")
        }
    }
}
