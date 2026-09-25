package com.adf.pirobinetterie.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.adf.pirobinetterie.data.FichiersProvider
import com.adf.pirobinetterie.pdf.Images
import java.io.File

/** Image en plein écran (toucher pour fermer). */
fun Activity.afficherImage(chemin: String, titre: String) {
    val bmp = Images.charger(chemin, 2000) ?: run { toast("Image illisible"); return }
    val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
    }
    val barre = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(texte(titre, 18f, true, Color.WHITE), lp(0, WRAP, 1f))
        addView(bouton("Fermer", Couleurs.MARINE_CLAIR) { d.dismiss() })
    }
    racine.addView(barre, lp(MATCH, WRAP))
    racine.addView(ImageView(this).apply {
        setImageBitmap(bmp)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setOnClickListener { d.dismiss() }
    }, lp(MATCH, 0, 1f))
    d.setContentView(racine)
    d.setOnDismissListener { bmp.recycle() }
    d.show()
}

/** Lecture d'un PDF dans l'app (pages rendues en images), avec "Ouvrir avec…" une autre app. */
fun Activity.afficherPdf(fichier: File, titre: String) {
    val pages = mutableListOf<Bitmap>()
    try {
        ParcelFileDescriptor.open(fichier, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                val largeur = resources.displayMetrics.widthPixels.coerceAtMost(2400)
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p ->
                        val bmp = Bitmap.createBitmap(largeur, largeur * p.height / p.width, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bmp)
                    }
                }
            }
        }
    } catch (e: Exception) {
        toast("PDF illisible : ${e.message}")
        return
    }

    val d = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF505866.toInt())
    }
    val barre = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Couleurs.MARINE)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(texte(titre, 18f, true, Color.WHITE), lp(0, WRAP, 1f))
        addView(bouton("Ouvrir avec…", Couleurs.MARINE_CLAIR) {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(FichiersProvider.uri(this@afficherPdf, fichier), "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Ouvrir le PDF"))
            } catch (_: ActivityNotFoundException) {
                toast("Aucune application PDF installée")
            }
        }, lp(WRAP, WRAP).marges(0, 0, dp(8), 0))
        addView(bouton("Fermer", Couleurs.VERT) { d.dismiss() })
    }
    racine.addView(barre, lp(MATCH, WRAP))
    val liste = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    for (bmp in pages) {
        liste.addView(ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
        }, lp(MATCH, WRAP).marges(0, 0, 0, dp(8)))
    }
    racine.addView(ScrollView(this).apply { addView(liste) }, lp(MATCH, 0, 1f))
    d.setContentView(racine)
    d.setOnDismissListener { pages.forEach { it.recycle() } }
    d.show()
}
