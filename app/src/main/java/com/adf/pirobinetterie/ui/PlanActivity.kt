package com.adf.pirobinetterie.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.adf.pirobinetterie.PiApp
import com.adf.pirobinetterie.data.Depot
import com.adf.pirobinetterie.data.PlotPlan
import com.adf.pirobinetterie.data.Taches
import com.adf.pirobinetterie.excel.PlotPlanImporter
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.pdf.Images

/**
 * Localisation d'un item sur le plot plan, en plein écran : zoom à deux doigts, bouton "Fermer"
 * et bouton "Modifier" (choix du plan, déplacement du point, forme rond/ovale ou
 * carré/rectangle, dimensions). L'enregistrement régénère l'image de la fiche (et du PDF).
 */
class PlanActivity : Activity() {

    private val depot: Depot get() = (application as PiApp).depot
    private lateinit var item: Item
    private var plot: PlotPlan? = null

    private lateinit var vue: PlanView
    private lateinit var titre: TextView
    private lateinit var boutonsConsultation: LinearLayout
    private lateinit var boutonsEdition: LinearLayout
    private lateinit var panneau: LinearLayout
    private lateinit var lignePlans: LinearLayout
    private lateinit var boutonRond: Button
    private lateinit var boutonCarre: Button
    private lateinit var barreLargeur: SeekBar
    private lateinit var barreHauteur: SeekBar
    private lateinit var texteLargeur: TextView
    private lateinit var texteHauteur: TextView
    private lateinit var lier: CheckBox

    private var bmp: Bitmap? = null
    private var uniteAffichee: String? = null

    /** Point en cours de modification (null hors mode Modifier). */
    private var edit: PlotPlanImporter.Point? = null
    private var majBarres = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = depot.projet?.item(intent.getStringExtra(EXTRA_ITEM).orEmpty())
        if (i == null) {
            finish()
            return
        }
        item = i
        plot = depot.plotPlan
        setContentView(construire())
        afficherConsultation()
    }

    override fun onDestroy() {
        super.onDestroy()
        bmp?.recycle()
    }

    // --- Écran ----------------------------------------------------------------------------

    private fun construire(): View {
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Couleurs.MARINE)
        }
        val barre = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        titre = texte("", 20f, true, Color.WHITE)
        barre.addView(titre, lp(0, WRAP, 1f))
        boutonsConsultation = LinearLayout(this).apply {
            addView(bouton("✎  Modifier", Couleurs.ORANGE) { commencerEdition() }, lp(WRAP, WRAP).marges(0, 0, dp(10), 0))
            addView(bouton("Fermer", Couleurs.MARINE_CLAIR) { finish() })
        }
        boutonsEdition = LinearLayout(this).apply {
            visibility = View.GONE
            addView(bouton("Annuler", Couleurs.MARINE_CLAIR) { afficherConsultation() }, lp(WRAP, WRAP).marges(0, 0, dp(10), 0))
            addView(bouton("✔  Enregistrer", Couleurs.VERT) { enregistrer() })
        }
        barre.addView(boutonsConsultation)
        barre.addView(boutonsEdition)
        racine.addView(barre, lp(MATCH, WRAP))

        vue = PlanView(this).apply { surDeplacement = { fx, fy -> modifier { it.copie(fx = fx, fy = fy) } } }
        racine.addView(vue, lp(MATCH, 0, 1f))

        panneau = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Couleurs.FOND)
            setPadding(dp(14), dp(8), dp(14), dp(10))
            visibility = View.GONE
        }
        lignePlans = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panneau.addView(HorizontalScrollView(this).apply { addView(lignePlans) }, lp(MATCH, WRAP).marges(0, 0, 0, dp(6)))

        val ligneForme = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        ligneForme.addView(texte("Forme :", 16f, true), lp(dp(110), WRAP))
        boutonRond = bouton("●  Rond / Ovale", Couleurs.MARINE_CLAIR) { modifier { it.copie(carre = false) } }
        boutonCarre = bouton("■  Carré / Rectangle", Couleurs.MARINE_CLAIR) { modifier { it.copie(carre = true) } }
        ligneForme.addView(boutonRond, lp(WRAP, WRAP).marges(0, 0, dp(10), 0))
        ligneForme.addView(boutonCarre, lp(WRAP, WRAP).marges(0, 0, dp(20), 0))
        lier = CheckBox(this).apply {
            text = "Largeur = hauteur (rond / carré)"
            textSize = 16f
            setOnCheckedChangeListener { _, coche ->
                if (coche) modifier { it.copie(fh = hauteurPour(it.fw, it)) }
            }
        }
        ligneForme.addView(lier)
        panneau.addView(ligneForme, lp(MATCH, WRAP).marges(0, 0, 0, dp(4)))

        fun ligneTaille(libelle: String, barre: SeekBar, valeur: TextView): View = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(texte(libelle, 16f, true), lp(dp(110), WRAP))
            addView(barre, lp(0, WRAP, 1f))
            addView(valeur, lp(dp(90), WRAP))
        }
        texteLargeur = texte("", 16f)
        texteHauteur = texte("", 16f)
        barreLargeur = SeekBar(this).apply { max = PAS_MAX }
        barreHauteur = SeekBar(this).apply { max = PAS_MAX }
        barreLargeur.setOnSeekBarChangeListener(ecouteur { v ->
            modifier { e -> val fw = fraction(v); e.copie(fw = fw, fh = if (lier.isChecked) hauteurPour(fw, e) else e.fh) }
        })
        barreHauteur.setOnSeekBarChangeListener(ecouteur { v ->
            modifier { e ->
                val fhL = fraction(v) // en fraction de la largeur du plan
                val fh = hauteurPour(fhL, e)
                if (lier.isChecked) e.copie(fw = fhL, fh = fh) else e.copie(fh = fh)
            }
        })
        panneau.addView(ligneTaille("Largeur :", barreLargeur, texteLargeur), lp(MATCH, WRAP))
        panneau.addView(ligneTaille("Hauteur :", barreHauteur, texteHauteur), lp(MATCH, WRAP))

        val bas = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bas.addView(texte("Faites glisser le point, ou touchez le plan à l'endroit voulu. Deux doigts pour zoomer, double-tap pour tout voir.", 14f, false, Couleurs.GRIS), lp(0, WRAP, 1f))
        bas.addView(bouton("Revenir au point de l'Excel", Couleurs.ROUGE) { revenirExcel() }.apply { textSize = 14f })
        panneau.addView(bas, lp(MATCH, WRAP).marges(0, dp(4), 0, 0))
        racine.addView(panneau, lp(MATCH, WRAP))
        return racine
    }

    private fun ecouteur(changement: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && !majBarres) changement(progress)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    // --- Consultation ---------------------------------------------------------------------

    private fun afficherConsultation() {
        edit = null
        vue.edition = false
        panneau.visibility = View.GONE
        boutonsEdition.visibility = View.GONE
        boutonsConsultation.visibility = View.VISIBLE
        val p = plot
        val points = p?.pointsPour(item).orEmpty()
        if (p != null && points.isNotEmpty()) {
            titre.text = "${item.nom} — localisation sur ${points.first().unite}"
            chargerPlan(points.first().unite)
            vue.points = points
            vue.post { vue.centrerSurPoint() }
        } else if (item.plan.isNotEmpty()) {
            // Pas de point sur le plot plan : image "plan" de l'item (dossier Photos) ou plan vierge.
            titre.text = "${item.nom} — plot plan"
            uniteAffichee = null
            remplacerBitmap(Images.charger(item.plan, 3000))
            vue.points = emptyList()
        } else if (p != null && p.plans.isNotEmpty()) {
            titre.text = "${item.nom} — pas encore localisé (touchez « Modifier » pour placer le point)"
            chargerPlan(p.plans.keys.first())
            vue.points = emptyList()
        } else {
            titre.text = "${item.nom} — aucun plot plan"
            remplacerBitmap(null)
            vue.points = emptyList()
        }
    }

    private fun chargerPlan(unite: String) {
        if (unite == uniteAffichee && bmp != null) return
        uniteAffichee = unite
        remplacerBitmap(plot?.plans?.get(unite)?.let { Images.charger(it, 3000) })
    }

    private fun remplacerBitmap(nouveau: Bitmap?) {
        val ancien = bmp
        bmp = nouveau
        vue.bitmap = nouveau
        if (ancien != null && ancien != nouveau) ancien.recycle()
    }

    // --- Modification ---------------------------------------------------------------------

    private fun commencerEdition() {
        val p = plot
        if (p == null || p.plans.isEmpty()) {
            message("Plot plan", "Importez d'abord le fichier PlotPlan (bouton « Plot plan » de l'écran principal) pour pouvoir placer le point.")
            return
        }
        val existant = p.pointsPour(item).firstOrNull()
        val unite = existant?.unite ?: uniteAffichee?.takeIf { it in p.plans } ?: p.plans.keys.first()
        chargerPlan(unite)
        val b = bmp ?: return
        // Point de départ : le point actuel, à sa taille affichée ; sinon un rond au centre du plan.
        val depart = if (existant != null) {
            val r = PlotPlan.rectangle(existant, b.width, b.height)
            val fw = (r.width() / b.width).toDouble()
            val fh = (r.height() / b.height).toDouble()
            existant.copie(fw = fw, fh = fh, etire = PlotPlan.estZone(fw, fh), manuel = true)
        } else {
            val couleur = p.couleurFamille(item[ChampsFiche.TYPE])
            val t = PlotPlan.TAILLE_MINI
            PlotPlanImporter.Point(item.nom, unite, item[ChampsFiche.TYPE], 0.5, 0.5, t, t * b.width / b.height, false, couleur, false, manuel = true)
        }
        edit = depart
        vue.edition = true
        vue.points = listOf(depart)
        lier.isChecked = Math.abs(depart.fw * b.width - depart.fh * b.height) < 1.0
        titre.text = "${item.nom} — modifier la localisation"
        boutonsConsultation.visibility = View.GONE
        boutonsEdition.visibility = View.VISIBLE
        panneau.visibility = View.VISIBLE
        majPlans()
        majCurseurs(depart)
        majFormes(depart)
        if (existant == null) toast("Touchez le plan à l'endroit de l'équipement pour y placer le point")
    }

    private fun modifier(transformation: (PlotPlanImporter.Point) -> PlotPlanImporter.Point) {
        val e = edit ?: return
        val n = transformation(e).let { it.copie(etire = PlotPlan.estZone(it.fw, it.fh)) }
        edit = n
        vue.points = listOf(n)
        majCurseurs(n)
        majFormes(n)
    }

    private fun majPlans() {
        lignePlans.removeAllViews()
        val p = plot ?: return
        lignePlans.addView(texte("Plan :", 16f, true), lp(dp(110), WRAP))
        for (unite in p.plans.keys) {
            val actif = unite == edit?.unite
            lignePlans.addView(bouton(unite, if (actif) Couleurs.VERT else Couleurs.MARINE_CLAIR) {
                if (edit?.unite != unite) {
                    chargerPlan(unite)
                    modifier { it.copie(unite = unite) }
                    majPlans()
                    vue.post { vue.ajuster() }
                }
            }, lp(WRAP, WRAP).marges(0, 0, dp(8), 0))
        }
        if (p.plans.size == 1) lignePlans.addView(texte("  (un seul plan dans le fichier PlotPlan)", 14f, false, Couleurs.GRIS))
    }

    private fun majFormes(e: PlotPlanImporter.Point) {
        boutonRond.background = fondCliquable(if (!e.carre) Couleurs.VERT else Couleurs.MARINE_CLAIR, Couleurs.MARINE, dp(8).toFloat())
        boutonCarre.background = fondCliquable(if (e.carre) Couleurs.VERT else Couleurs.MARINE_CLAIR, Couleurs.MARINE, dp(8).toFloat())
    }

    private fun majCurseurs(e: PlotPlanImporter.Point) {
        val b = bmp ?: return
        majBarres = true
        barreLargeur.progress = pas(e.fw)
        val hL = e.fh * b.height / b.width // hauteur en fraction de la largeur du plan
        barreHauteur.progress = pas(hL)
        majBarres = false
        texteLargeur.text = pourcent(e.fw)
        texteHauteur.text = pourcent(hL)
    }

    /** Hauteur (fraction de la hauteur du plan) donnant [fl] fois la largeur du plan. */
    private fun hauteurPour(fl: Double, e: PlotPlanImporter.Point): Double {
        val b = bmp ?: return e.fh
        return fl * b.width / b.height
    }

    private fun revenirExcel() {
        if (plot?.pointsDe(item.nom).isNullOrEmpty()) {
            toast("${item.nom} n'a pas de point dans le fichier PlotPlan")
            return
        }
        confirmer("Revenir au point de l'Excel ?", "La position, la forme et la taille modifiées sur la tablette seront abandonnées.", "Revenir") {
            appliquer(null)
        }
    }

    private fun enregistrer() {
        val e = edit ?: return
        appliquer(e)
    }

    /** [point] = null : supprime la modification (retour au point du fichier PlotPlan). */
    private fun appliquer(point: PlotPlanImporter.Point?) {
        if (point == null) item.v.remove(PlotPlan.CLE_LOCALISATION) else item[PlotPlan.CLE_LOCALISATION] = PlotPlan.versTexte(point)
        val attente = attente("Mise à jour du plot plan de ${item.nom}…")
        Taches.lancer({ depot.rendreLocalisation(item) }, { chemin ->
            attente.dismiss()
            if (chemin != null) item.plan = chemin
            depot.sauver()
            setResult(RESULT_OK)
            toast("Localisation enregistrée")
            afficherConsultation()
        }, { ex ->
            attente.dismiss()
            message("Plot plan", "Mise à jour impossible : ${ex.message}")
        })
    }

    companion object {
        const val EXTRA_ITEM = "item"

        /** Curseurs : 0,4 % à 25 % de la largeur du plan, par pas de 0,1 %. */
        private const val PAS_MIN = 4
        private const val PAS_MAX = 250 - PAS_MIN

        private fun fraction(pas: Int): Double = (pas + PAS_MIN) / 1000.0
        private fun pas(fraction: Double): Int = (Math.round(fraction * 1000).toInt() - PAS_MIN).coerceIn(0, PAS_MAX)
        private fun pourcent(f: Double): String = String.format(java.util.Locale.FRANCE, "%.1f %%", f * 100)
    }
}

/** Copie d'un point avec quelques valeurs changées. */
fun PlotPlanImporter.Point.copie(
    unite: String = this.unite, fx: Double = this.fx, fy: Double = this.fy, fw: Double = this.fw, fh: Double = this.fh,
    carre: Boolean = this.carre, etire: Boolean = this.etire, manuel: Boolean = this.manuel
) = PlotPlanImporter.Point(tag, unite, famille, fx, fy, fw, fh, carre, couleur, etire, manuel)
