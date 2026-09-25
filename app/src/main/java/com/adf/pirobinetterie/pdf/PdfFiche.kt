package com.adf.pirobinetterie.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Génère le PDF d'une fiche (une page A4 paysage par tranche de 5 brides). */
object PdfFiche {

    fun generer(d: DonneesFiche, destination: File) {
        val doc = PdfDocument()
        try {
            for (page in 0 until FicheLayout.nombrePages(d)) {
                val info = PdfDocument.PageInfo.Builder(FicheLayout.LARGEUR.toInt(), FicheLayout.HAUTEUR.toInt(), page + 1).create()
                val p = doc.startPage(info)
                FicheLayout.dessinerPage(ToileAndroid(p.canvas), d, page)
                doc.finishPage(p)
            }
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}

class ToileAndroid(private val canvas: Canvas) : Toile {

    private val fond = Paint().apply { style = Paint.Style.FILL }
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun police(taille: Float, gras: Boolean) {
        texte.textSize = taille
        texte.typeface = if (gras) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, fond: Int?, bordure: Int?, epaisseur: Float) {
        if (fond != null) {
            this.fond.color = fond
            canvas.drawRect(x, y, x + w, y + h, this.fond)
        }
        if (bordure != null) {
            trait.color = bordure
            trait.strokeWidth = epaisseur
            canvas.drawRect(x, y, x + w, y + h, trait)
        }
    }

    override fun largeurTexte(texte: String, taille: Float, gras: Boolean): Float {
        police(taille, gras)
        return this.texte.measureText(texte)
    }

    override fun texte(texte: String, x: Float, yBase: Float, taille: Float, gras: Boolean, couleur: Int) {
        police(taille, gras)
        this.texte.color = couleur
        canvas.drawText(texte, x, yBase, this.texte)
    }

    override fun image(chemin: String, x: Float, y: Float, w: Float, h: Float): Boolean {
        // ~200 dpi dans le PDF : assez net à l'impression sans alourdir le fichier.
        val bmp = Images.charger(chemin, (maxOf(w, h) * 2.8f).toInt()) ?: return false
        try {
            val echelle = minOf(w / bmp.width, h / bmp.height)
            val iw = bmp.width * echelle
            val ih = bmp.height * echelle
            val left = x + (w - iw) / 2
            val top = y + (h - ih) / 2
            canvas.drawBitmap(bmp, null, RectF(left, top, left + iw, top + ih), image)
        } finally {
            bmp.recycle()
        }
        return true
    }
}

/** Chargement d'images réduit à la taille utile, en respectant l'orientation de la photo (EXIF). */
object Images {

    fun charger(chemin: String, tailleMax: Int): Bitmap? {
        val f = File(chemin)
        if (!f.exists()) return null
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(chemin, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
        var echantillon = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= tailleMax) echantillon *= 2
        val bmp = BitmapFactory.decodeFile(chemin, BitmapFactory.Options().apply { inSampleSize = echantillon }) ?: return null
        val rotation = try {
            when (ExifInterface(chemin).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
        if (rotation == 0f) return bmp
        val tourne = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        if (tourne != bmp) bmp.recycle()
        return tourne
    }
}
