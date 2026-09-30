package com.adf.pirobinetterie.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.adf.pirobinetterie.excel.PlotPlanImporter
import com.adf.pirobinetterie.model.Etat
import com.adf.pirobinetterie.model.Item
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
 *
 * Un point peut être modifié sur la tablette (plan, position, forme, dimensions) : il est alors
 * enregistré dans l'item lui-même (clé [CLE_LOCALISATION]), suit ses révisions et prime sur
 * le fichier Excel, y compris après un nouvel import du plot plan.
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

    /** Couleur d'une famille (d'après les points de l'Excel), gris si inconnue. */
    fun couleurFamille(famille: String): Int =
        points.firstOrNull { it.famille.trim().equals(famille.trim(), ignoreCase = true) }?.couleur ?: GRIS

    /** Point(s) à dessiner pour [item] : localisation modifiée sur la tablette, sinon l'Excel. */
    fun pointsPour(item: Item): List<PlotPlanImporter.Point> {
        localisationManuelle(item)?.let { m -> return if (m.unite in plans) listOf(m) else emptyList() }
        val excel = pointsDe(item.nom)
        val unite = excel.firstOrNull()?.unite ?: return emptyList()
        return excel.filter { it.unite == unite }
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
        /** Clé (dans les valeurs de l'item) de la localisation modifiée à la main. */
        const val CLE_LOCALISATION = "_localisation"

        private const val GRIS = 0xFF6E6E6E.toInt()

        /** Largeur de l'image de localisation générée pour chaque item (pixels). */
        private const val LARGEUR_RENDU = 1400

        /** Diamètre minimal d'un point venant de l'Excel (fraction de la largeur du plan). */
        const val TAILLE_MINI = 0.03

        /** Taille minimale d'un point dimensionné à la main (fraction de la largeur du plan). */
        const val TAILLE_MINI_MANUELLE = 0.004

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

        // --- Localisation modifiée à la main ----------------------------------------------

        /** "unite|fx|fy|fw|fh|C ou R|couleur" (fractions de la largeur / hauteur du plan). */
        fun versTexte(p: PlotPlanImporter.Point): String =
            listOf(p.unite, p.fx, p.fy, p.fw, p.fh, if (p.carre) "C" else "R", Integer.toHexString(p.couleur)).joinToString("|")

        fun localisationManuelle(item: Item): PlotPlanImporter.Point? {
            val t = item[CLE_LOCALISATION].split("|")
            if (t.size < 7) return null
            val fx = t[1].toDoubleOrNull() ?: return null
            val fy = t[2].toDoubleOrNull() ?: return null
            val fw = t[3].toDoubleOrNull() ?: return null
            val fh = t[4].toDoubleOrNull() ?: return null
            val couleur = t[6].toLongOrNull(16)?.toInt() ?: GRIS
            return PlotPlanImporter.Point(item.nom, t[0], "", fx, fy, fw, fh, t[5] == "C", couleur, estZone(fw, fh), manuel = true)
        }

        /** Ovale / rectangle ou grand point = zone : dessiné semi-transparent pour voir le plan. */
        fun estZone(fw: Double, fh: Double): Boolean = fw > 0.06 || fh > 0.06

        /**
         * Rectangle du point en pixels d'une image [largeur] x [hauteur] : taille réelle pour un
         * point modifié à la main, grossie à [TAILLE_MINI] pour un point de l'Excel.
         */
        fun rectangle(p: PlotPlanImporter.Point, largeur: Int, hauteur: Int): RectF {
            val w: Double
            val h: Double
            if (p.manuel) {
                w = maxOf(p.fw, TAILLE_MINI_MANUELLE) * largeur
                h = maxOf(p.fh * hauteur, TAILLE_MINI_MANUELLE * largeur)
            } else {
                val mini = TAILLE_MINI * largeur
                w = maxOf(p.fw * largeur, mini)
                h = maxOf(p.fh * hauteur, if (p.etire) 0.0 else mini)
            }
            val cx = p.fx * largeur
            val cy = p.fy * hauteur
            return RectF((cx - w / 2).toFloat(), (cy - h / 2).toFloat(), (cx + w / 2).toFloat(), (cy + h / 2).toFloat())
        }

        /** Dessine les points sur un canevas dont l'image de fond fait [largeur] x [hauteur] pixels. */
        fun dessinerPoints(c: Canvas, points: List<PlotPlanImporter.Point>, largeur: Int, hauteur: Int, epaisseur: Float) {
            val remplissage = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val contour = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = epaisseur
            }
            for (p in points) {
                val r = rectangle(p, largeur, hauteur)
                // Zone (ovale / rectangle étiré) : semi-transparente, contour de sa couleur.
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
        }

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
                    val points = plot.pointsPour(item)
                    if (points.isEmpty()) continue
                    val unite = points.first().unite
                    val fond = fonds.getOrPut(unite) { plot.plans[unite]?.let { Images.charger(it, LARGEUR_RENDU) } } ?: continue
                    val dest = File(dossier, "${Depot.nomFichier(item.nom)}_localisation_$horodatage.jpg")
                    rendre(fond, points, dest)
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

        /** Image de localisation d'un seul item (après modification sur la tablette). */
        fun rendreItem(plot: PlotPlan, item: Item, dest: File): Boolean {
            val points = plot.pointsPour(item)
            if (points.isEmpty()) return false
            val fond = plot.plans[points.first().unite]?.let { Images.charger(it, LARGEUR_RENDU) } ?: return false
            try {
                dest.parentFile?.mkdirs()
                rendre(fond, points, dest)
            } finally {
                fond.recycle()
            }
            return true
        }

        private fun rendre(fond: Bitmap, points: List<PlotPlanImporter.Point>, dest: File) {
            val largeur = minOf(LARGEUR_RENDU, fond.width)
            val hauteur = Math.round(fond.height * largeur.toDouble() / fond.width).toInt()
            val bmp = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                c.drawBitmap(fond, null, RectF(0f, 0f, largeur.toFloat(), hauteur.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                dessinerPoints(c, points, largeur, hauteur, maxOf(3f, largeur / 400f))
                FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            } finally {
                bmp.recycle()
            }
        }
    }
}
