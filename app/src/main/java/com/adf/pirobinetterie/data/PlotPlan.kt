package com.adf.pirobinetterie.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.adf.pirobinetterie.excel.PlotPlanImporter
import com.adf.pirobinetterie.model.Etat
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.cle
import com.adf.pirobinetterie.pdf.Images
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Plot plan importé depuis le fichier Excel "PlotPlan" : images des plans (une par unité) et
 * points des équipements. Sert à fabriquer, pour chaque item dont l'Équipement / TAG correspond
 * au nom, l'image "localisation sur plot plan" : le plan + le rond ou le carré de l'item, à la
 * couleur de sa famille (sans légende ni trait).
 */
class PlotPlan(
    val fichier: String,
    val date: String,
    /** Unité -> image du plan copiée dans l'app. */
    val plans: Map<String, String>,
    val points: List<PlotPlanImporter.Point>
) {
    fun pointsDe(nomItem: String): List<PlotPlanImporter.Point> {
        val k = cle(nomItem)
        return points.filter { cle(it.tag) == k }
    }

    fun json(): String = JSONObject().apply {
        put("fichier", fichier)
        put("date", date)
        put("plans", JSONObject().apply { plans.forEach { (u, c) -> put(u, c) } })
        put("points", JSONArray().apply {
            points.forEach { p ->
                put(JSONObject().apply {
                    put("tag", p.tag); put("unite", p.unite); put("famille", p.famille)
                    put("fx", p.fx); put("fy", p.fy); put("fw", p.fw); put("fh", p.fh)
                    put("carre", p.carre); put("couleur", p.couleur); put("etire", p.etire)
                })
            }
        })
    }.toString()

    companion object {
        fun lire(texte: String): PlotPlan {
            val o = JSONObject(texte)
            val plans = linkedMapOf<String, String>()
            o.getJSONObject("plans").let { p -> p.keys().forEach { plans[it] = p.getString(it) } }
            val a = o.getJSONArray("points")
            val points = (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                PlotPlanImporter.Point(
                    p.getString("tag"), p.getString("unite"), p.optString("famille"),
                    p.getDouble("fx"), p.getDouble("fy"), p.getDouble("fw"), p.getDouble("fh"),
                    p.getBoolean("carre"), p.getInt("couleur"), p.optBoolean("etire")
                )
            }
            return PlotPlan(o.optString("fichier"), o.optString("date"), plans, points)
        }

        /** Largeur de l'image de localisation générée pour chaque item (pixels). */
        private const val LARGEUR_RENDU = 1400

        /** Diamètre minimal d'un point (fraction de la largeur du plan), pour rester visible en petit. */
        private const val TAILLE_MINI = 0.03

        /**
         * Applique le plot plan au projet : chaque item localisé reçoit son image "plan + point".
         * Un item jamais validé ne passe pas "en cours" pour autant (sa référence d'import suit).
         * Retourne le nombre d'items localisés. À exécuter en arrière-plan.
         */
        fun appliquer(plot: PlotPlan, projet: Projet, dossier: File, horodatage: String): Int {
            dossier.mkdirs()
            val fonds = mutableMapOf<String, Bitmap?>()
            var n = 0
            try {
                for (item in projet.items) {
                    val points = plot.pointsDe(item.nom)
                    if (points.isEmpty()) continue
                    val unite = points.first().unite
                    val fond = fonds.getOrPut(unite) { plot.plans[unite]?.let { Images.charger(it, LARGEUR_RENDU) } } ?: continue
                    val dest = File(dossier, "${Depot.nomFichier(item.nom)}_localisation_$horodatage.jpg")
                    dessiner(fond, points.filter { it.unite == unite }, dest)
                    val ancien = item.plan
                    item.plan = dest.absolutePath
                    if (item.revisions.isEmpty() && item.initial.plan == ancien) {
                        item.initial = Etat(item.initial.v, item.initial.brides, item.initial.photo, item.plan)
                    }
                    n++
                }
            } finally {
                fonds.values.forEach { it?.recycle() }
            }
            return n
        }

        private fun dessiner(fond: Bitmap, points: List<PlotPlanImporter.Point>, dest: File) {
            val largeur = minOf(LARGEUR_RENDU, fond.width)
            val hauteur = Math.round(fond.height * largeur.toDouble() / fond.width).toInt()
            val bmp = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                c.drawBitmap(fond, null, RectF(0f, 0f, largeur.toFloat(), hauteur.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                val remplissage = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                val contour = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(3f, largeur / 400f)
                }
                for (p in points) {
                    val mini = TAILLE_MINI * largeur
                    val w = maxOf(p.fw * largeur, mini)
                    val h = maxOf(p.fh * hauteur, if (p.etire) 0.0 else mini)
                    val cx = p.fx * largeur
                    val cy = p.fy * hauteur
                    val r = RectF((cx - w / 2).toFloat(), (cy - h / 2).toFloat(), (cx + w / 2).toFloat(), (cy + h / 2).toFloat())
                    // Point étiré (zone) : semi-transparent pour voir le plan dessous, comme dans l'Excel.
                    remplissage.color = if (p.etire) (p.couleur and 0x00FFFFFF) or (0x8C shl 24) else p.couleur
                    contour.color = if (p.etire) p.couleur else Color.BLACK
                    if (p.carre) {
                        c.drawRect(r, remplissage)
                        c.drawRect(r, contour)
                    } else {
                        c.drawOval(r, remplissage)
                        c.drawOval(r, contour)
                    }
                }
                FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            } finally {
                bmp.recycle()
            }
        }
    }
}
