package com.adf.pirobinetterie.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.adf.pirobinetterie.PiApp
import com.adf.pirobinetterie.data.Depot
import com.adf.pirobinetterie.data.FichiersProvider
import com.adf.pirobinetterie.data.Taches
import com.adf.pirobinetterie.model.Bride
import com.adf.pirobinetterie.model.Champ
import com.adf.pirobinetterie.model.ChampsBride
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.Diff
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.Statut
import com.adf.pirobinetterie.pdf.Images
import java.io.File

/**
 * Fiche récap d'un item : même organisation que l'onglet "Fiche" de l'Excel. Toutes les cases
 * sont modifiables ; les modifications depuis la dernière révision validée sont en jaune. Le
 * bouton "Valider" fige la révision en cours et génère son PDF.
 */
class FicheActivity : Activity() {

    private val depot: Depot get() = (application as PiApp).depot
    private lateinit var projet: Projet
    private lateinit var item: Item

    private lateinit var contenu: LinearLayout
    private lateinit var statut: TextView

    /** Photo en cours de prise (conservé si l'app est mise en pause par l'appareil photo). */
    private var photoEnCours: String? = null

    /** Images déjà chargées (la fiche est reconstruite à chaque modification). */
    private val cacheImages = mutableMapOf<String, android.graphics.Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = depot.projet
        val i = p?.item(intent.getStringExtra(EXTRA_ITEM).orEmpty())
        if (p == null || i == null) {
            finish()
            return
        }
        projet = p
        item = i
        photoEnCours = savedInstanceState?.getString("photoEnCours")
        setContentView(construire())
        afficher()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("photoEnCours", photoEnCours)
    }

    private fun construire(): View {
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Couleurs.FOND)
        }
        val barre = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Couleurs.MARINE)
            setPadding(dp(12), dp(10), dp(14), dp(10))
        }
        barre.addView(bouton("←  Liste", Couleurs.MARINE_CLAIR) { finish() })
        barre.addView(texte(item.nom, 26f, true, Color.WHITE).apply { setPadding(dp(18), 0, dp(14), 0) })
        statut = badge("", Couleurs.GRIS)
        barre.addView(statut)
        barre.addView(View(this), lp(0, 1, 1f))
        barre.addView(bouton("PDF", Couleurs.MARINE_CLAIR) { choisirPdf() }, lp(WRAP, WRAP).marges(dp(8), 0, 0, 0))
        barre.addView(bouton("✔  VALIDER", Couleurs.VERT) { valider() }.apply { textSize = 18f }, lp(WRAP, WRAP).marges(dp(10), 0, 0, 0))
        racine.addView(barre, lp(MATCH, WRAP))

        contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(24))
        }
        racine.addView(ScrollView(this).apply { addView(contenu) }, lp(MATCH, 0, 1f))
        return racine
    }

    /** Reconstruit toute la fiche (appelé après chaque modification). */
    private fun afficher() {
        val s = item.statut()
        statut.text = when (s) {
            Statut.A_VALIDER -> "À valider (Rév. 0)"
            Statut.EN_COURS -> "Rév. 0 en cours"
            Statut.REVISION_EN_COURS -> "Rév. ${item.numEnCours} en cours"
            Statut.VALIDE -> "Validé — Rév. ${item.derniere?.num}"
        }
        statut.background = fondArrondi(
            when (s) {
                Statut.VALIDE -> Couleurs.VERT
                Statut.A_VALIDER -> Couleurs.GRIS
                else -> Couleurs.ORANGE
            }, dp(14).toFloat()
        )

        val ref = item.reference()
        val diff = if (ref != null) Diff.entre(ref, item.etatCourant()) else Diff.VIDE
        val defilement = (contenu.parent as? ScrollView)?.scrollY ?: 0
        contenu.removeAllViews()

        val haut = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val gauche = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gauche.addView(carteRevisions(), lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))
        gauche.addView(carteChamps("Donnée technique", ChampsFiche.donneesTechniques, diff), lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))
        gauche.addView(carteChamps("Besoins", ChampsFiche.besoins, diff), lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))
        gauche.addView(carteChamps("Travaux", ChampsFiche.travaux, diff), lp(MATCH, WRAP))
        haut.addView(gauche, lp(0, WRAP, 1f).marges(0, 0, dp(12), 0))
        haut.addView(carteImages(diff), lp(0, WRAP, 1.3f))
        contenu.addView(haut, lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))

        contenu.addView(carteBrides(diff), lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))
        contenu.addView(carteCommentaire(diff), lp(MATCH, WRAP).marges(0, 0, 0, dp(12)))
        contenu.addView(carteAutres(diff), lp(MATCH, WRAP))
        (contenu.parent as? ScrollView)?.post { (contenu.parent as? ScrollView)?.scrollTo(0, defilement) }
    }

    // --- Cartes ---------------------------------------------------------------------------

    private fun carteRevisions(): View {
        val c = carte("Révisions")
        val entetes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Rév." to 0.15f, "Date" to 0.3f, "Objet" to 0.55f).forEach { (t, poids) ->
            entetes.addView(texte(t, 15f, true, Color.WHITE).apply {
                setBackgroundColor(Couleurs.MARINE)
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }, lp(0, WRAP, poids).marges(0, 0, dp(2), 0))
        }
        c.addView(entetes)
        val revs = item.revisions.sortedBy { it.num }.takeLast(4)
        if (revs.isEmpty()) c.addView(texte("Aucune révision validée", 15f, false, Couleurs.GRIS).apply { setPadding(dp(8), dp(8), 0, dp(4)) })
        for (r in revs) {
            val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(r.num.toString() to 0.15f, r.date to 0.3f, r.objet to 0.55f).forEach { (t, poids) ->
                l.addView(texte(t, 15f).apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }, lp(0, WRAP, poids))
            }
            c.addView(l)
        }
        if (item.modifieDepuisValidation() || item.revisions.isEmpty()) {
            c.addView(texte("Rév. ${item.numEnCours} en cours de préparation — « Valider » pour la figer et générer le PDF.", 14f, true, Couleurs.ORANGE)
                .apply { setPadding(dp(8), dp(8), 0, 0) })
        }
        return c
    }

    private fun carteChamps(titre: String, champs: List<Champ>, diff: Diff): View {
        val c = carte(titre)
        for (ch in champs) c.addView(ligneChamp(ch.libelle, item[ch.cle], diff.champ(ch.cle)) { editerChamp(ch.cle, ch.libelle, ch.liste) })
        return c
    }

    private fun carteCommentaire(diff: Diff): View {
        val c = carte("Commentaire")
        val cle = ChampsFiche.COMMENTAIRE
        val v = item[cle]
        c.addView(texte(v.ifEmpty { "Toucher pour ajouter un commentaire" }, 17f, false, if (v.isEmpty()) Couleurs.GRIS else Couleurs.TEXTE).apply {
            minHeight = dp(80)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = fondCliquable(if (diff.champ(cle)) Couleurs.JAUNE else Couleurs.LIBELLE, 0xFFDDE6F3.toInt(), dp(6).toFloat())
            setOnClickListener {
                saisir("Commentaire", item[cle], emptyList(), multiligne = true) { modifierChamp(cle, it) }
            }
        })
        return c
    }

    /** Colonnes de Suivi qui ne figurent pas sur la fiche (Num, Chrono, Priorité, Circuit...). */
    private fun carteAutres(diff: Diff): View {
        val c = carte("Autres informations (onglet Suivi)")
        val cles = projet.colonnesSuivi.filterKeys { it !in ChampsFiche.clesFiche }
        for ((cle, libelle) in cles) {
            c.addView(ligneChamp(libelle, item[cle], diff.champ(cle)) { editerChamp(cle, libelle, ChampsFiche.listePour(cle)) })
        }
        return c
    }

    private fun carteImages(diff: Diff): View {
        val c = carte("Photo  (3/4)  et localisation sur plot plan  (1/4)")
        c.addView(cadreImage(item.photo, "Aucune photo", dp(420), diff.photo) { item.photo.takeIf { it.isNotEmpty() }?.let { afficherImage(it, "${item.nom} — photo") } })
        val bPhoto = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bPhoto.addView(bouton("📷  Prendre la photo", Couleurs.MARINE) { prendrePhoto() }, lp(0, WRAP, 1f).marges(0, dp(8), dp(6), dp(12)))
        bPhoto.addView(bouton("Choisir un fichier", Couleurs.MARINE_CLAIR) { choisirImage(REQ_FICHIER_PHOTO) }, lp(0, WRAP, 1f).marges(dp(6), dp(8), 0, dp(12)))
        c.addView(bPhoto)
        c.addView(cadreImage(item.plan, "Aucun plot plan", dp(150), diff.plan) { item.plan.takeIf { it.isNotEmpty() }?.let { afficherImage(it, "${item.nom} — plot plan") } })
        c.addView(bouton("Remplacer le plot plan", Couleurs.MARINE_CLAIR) { choisirImage(REQ_FICHIER_PLAN) }, lp(MATCH, WRAP).marges(0, dp(8), 0, 0))
        return c
    }

    private fun cadreImage(chemin: String, vide: String, hauteur: Int, jaune: Boolean, clic: () -> Unit): View {
        val cadre = FrameLayout(this).apply {
            background = fondArrondi(0xFFE9EDF2.toInt(), dp(6).toFloat(), if (jaune) Couleurs.JAUNE else Couleurs.BORDURE, if (jaune) dp(5) else dp(1))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { clic() }
        }
        val bmp = if (chemin.isEmpty()) null else cacheImages[chemin] ?: Images.charger(chemin, 1400)?.also {
            if (cacheImages.size >= 4) cacheImages.clear()
            cacheImages[chemin] = it
        }
        if (bmp != null) {
            cadre.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            cadre.addView(texte(vide, 17f, false, Couleurs.GRIS).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        cadre.layoutParams = lp(MATCH, hauteur)
        return cadre
    }

    private fun carteBrides(diff: Diff): View {
        val c = carte("Brides (${item.brides.size})")
        val colonnes = ChampsBride.colonnesEdition
        fun largeur(cle: String) = when (cle) {
            ChampsBride.REP -> dp(80)
            ChampsBride.DESIGNATION -> dp(170)
            else -> dp(92)
        }
        val tableau = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val entete = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (col in colonnes) {
            entete.addView(texte(col.libelle, 14f, true, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(Couleurs.MARINE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }, lp(largeur(col.cle), MATCH).marges(0, 0, dp(2), 0))
        }
        entete.addView(texte("", 14f).apply { setBackgroundColor(Couleurs.MARINE) }, lp(dp(200), MATCH))
        tableau.addView(entete, lp(WRAP, WRAP).marges(0, 0, 0, dp(2)))

        for (b in item.brides) {
            val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in colonnes) {
                val v = b[col.cle]
                val jaune = diff.cellule(b.id, col.cle)
                ligne.addView(texte(v, 16f, col.cle == ChampsBride.REP).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(50)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    background = fondCliquable(if (jaune) Couleurs.JAUNE else if (col.cle == ChampsBride.REP) Couleurs.LIBELLE else Color.WHITE, 0xFFDDE6F3.toInt(), 0f, Couleurs.BORDURE, 1)
                    setOnClickListener { editerBride(b, col) }
                }, lp(largeur(col.cle), MATCH).marges(0, 0, dp(2), 0))
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actions.addView(bouton("↻ ABBAQUE", Couleurs.MARINE_CLAIR) { recalculer(b, silencieux = false) }.apply { textSize = 14f }, lp(dp(120), WRAP).marges(dp(4), 0, dp(4), 0))
            actions.addView(bouton("🗑", Couleurs.ROUGE) { supprimerBride(b) }.apply { textSize = 16f }, lp(dp(64), WRAP))
            ligne.addView(actions, lp(dp(200), WRAP))
            tableau.addView(ligne, lp(WRAP, WRAP).marges(0, 0, 0, dp(2)))
        }
        c.addView(HorizontalScrollView(this).apply { addView(tableau) })

        if (diff.bridesSupprimees.isNotEmpty()) {
            val noms = diff.bridesSupprimees.joinToString(", ") { listOf(it[ChampsBride.REP], it[ChampsBride.DESIGNATION]).filter { s -> s.isNotBlank() }.joinToString(" ") }
            c.addView(texte("Supprimée(s) depuis la Rév. ${item.derniere?.num} : $noms", 15f, true, Couleurs.ROUGE).apply { setPadding(0, dp(8), 0, 0) })
        }
        c.addView(bouton("+  Ajouter une bride", Couleurs.VERT) { ajouterBride() }, lp(WRAP, WRAP).marges(0, dp(10), 0, 0))
        c.addView(texte("Boulonnerie calculée d'après l'ABBAQUE (DN + PN, longueur RF + ${com.adf.pirobinetterie.model.Abaque.nombre(depot.rondelleMm)} mm si rondelle) ; valeurs modifiables à la main.", 13f, false, Couleurs.GRIS)
            .apply { setPadding(0, dp(6), 0, 0) })
        return c
    }

    // --- Modifications --------------------------------------------------------------------

    private fun editerChamp(cle: String, libelle: String, liste: String?) {
        saisir(libelle, item[cle], liste?.let { projet.listes[it] }.orEmpty()) { modifierChamp(cle, it) }
    }

    private fun modifierChamp(cle: String, valeur: String) {
        if (item[cle] == valeur) return
        if (valeur.isEmpty() && cle !in item.v) return
        item[cle] = valeur
        depot.sauver()
        afficher()
    }

    private fun editerBride(b: Bride, col: Champ) {
        val options = col.liste?.let { projet.listes[it] }.orEmpty()
        saisir("${b[ChampsBride.REP].ifEmpty { "Bride" }} — ${col.libelle}", b[col.cle], options) { v ->
            if (b[col.cle] == v) return@saisir
            b[col.cle] = v
            if (col.cle == ChampsBride.DN || col.cle == ChampsBride.PN || col.cle == ChampsBride.RONDELLE) recalculer(b, silencieux = true)
            depot.sauver()
            afficher()
        }
    }

    private fun recalculer(b: Bride, silencieux: Boolean) {
        val ok = projet.abaque.calcule(b, depot.rondelleMm)
        if (ok) {
            if (!silencieux) {
                depot.sauver()
                afficher()
            }
            toast("Boulonnerie ABBAQUE : ${b[ChampsBride.QTE]} tiges ${b[ChampsBride.DIAM]} x ${b[ChampsBride.LG]} mm")
        } else if (!silencieux || (b[ChampsBride.DN].isNotBlank() && b[ChampsBride.PN].isNotBlank())) {
            toast("DN ${b[ChampsBride.DN]} / PN ${b[ChampsBride.PN]} absent de l'ABBAQUE : saisir la boulonnerie à la main")
        }
    }

    private fun ajouterBride() {
        val n = item.brides.size + 1
        var rep = "B$n"
        var k = n
        while (item.brides.any { it[ChampsBride.REP].equals(rep, true) }) rep = "B${++k}"
        val b = Bride()
        b[ChampsBride.UNITE] = item[ChampsFiche.UNITE]
        b[ChampsBride.FAMILLE] = item[ChampsFiche.TYPE]
        b[ChampsBride.ITEM] = item.nom
        b[ChampsBride.REP] = rep
        item.brides.add(b)
        depot.sauver()
        afficher()
        toast("Bride $rep ajoutée : touchez ses cases pour la renseigner")
    }

    private fun supprimerBride(b: Bride) {
        confirmer("Supprimer la bride ${b[ChampsBride.REP]} ?", "Elle sera retirée de la fiche et de l'onglet Matos à l'export.", "Supprimer") {
            item.brides.remove(b)
            depot.sauver()
            afficher()
        }
    }

    // --- Photos ---------------------------------------------------------------------------

    private fun prendrePhoto() {
        val f = depot.nouvellePhoto(item, "photo")
        val uri = FichiersProvider.uri(this, f)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            photoEnCours = f.absolutePath
            startActivityForResult(intent, REQ_PHOTO)
        } catch (_: ActivityNotFoundException) {
            photoEnCours = null
            toast("Aucune application appareil photo disponible")
        }
    }

    private fun choisirImage(requete: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, requete)
        } catch (_: ActivityNotFoundException) {
            toast("Aucun sélecteur de fichiers disponible")
        }
    }

    @Deprecated("API framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PHOTO -> {
                val f = photoEnCours?.let { File(it) }
                photoEnCours = null
                if (resultCode == RESULT_OK && f != null && f.length() > 0) {
                    item.photo = f.absolutePath
                    depot.sauver()
                    afficher()
                } else f?.delete()
            }
            REQ_FICHIER_PHOTO, REQ_FICHIER_PLAN -> {
                val uri = data?.data ?: return
                if (resultCode != RESULT_OK) return
                val genre = if (requestCode == REQ_FICHIER_PLAN) "plan" else "photo"
                val ext = when (contentResolver.getType(uri)) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val dest = File(depot.nouvellePhoto(item, genre).absolutePath.substringBeforeLast('.') + "." + ext)
                try {
                    contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                } catch (e: Exception) {
                    toast("Image illisible : ${e.message}")
                    return
                }
                if (dest.length() == 0L) return
                if (genre == "plan") item.plan = dest.absolutePath else item.photo = dest.absolutePath
                depot.sauver()
                afficher()
            }
        }
    }

    // --- Validation et PDF ----------------------------------------------------------------

    private fun valider() {
        val derniere = item.derniere
        if (derniere != null && !item.modifieDepuisValidation()) {
            confirmer(
                "Aucune modification",
                "Rien n'a changé depuis la Rév. ${derniere.num} du ${derniere.date}.\nRégénérer le PDF de la Rév. ${derniere.num} ?",
                "Régénérer"
            ) { genererPdf(derniere.num) }
            return
        }
        val num = item.numEnCours
        val contenuDialogue = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        contenuDialogue.addView(texte("Objet de la révision", 15f, true, Couleurs.GRIS))
        val objet = EditText(this).apply {
            setText(if (num == 0) "Initial" else "")
            hint = "Ex. : Modification DN bride B2, ajout échafaudage"
            textSize = 19f
            setSelection(text.length)
        }
        contenuDialogue.addView(objet, lp(MATCH, WRAP))
        if (item.photo.isEmpty() || item.plan.isEmpty()) {
            contenuDialogue.addView(texte("\nAttention : ${listOfNotNull(if (item.photo.isEmpty()) "photo" else null, if (item.plan.isEmpty()) "plot plan" else null).joinToString(" et ")} manquant(s).", 15f, true, Couleurs.ORANGE))
        }
        val d = AlertDialog.Builder(this)
            .setTitle("Valider la Rév. $num de ${item.nom}")
            .setView(contenuDialogue)
            .setPositiveButton("Valider et générer le PDF", null)
            .setNegativeButton("Annuler", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val texteObjet = objet.text.toString().trim()
                if (texteObjet.isEmpty()) {
                    objet.error = "Objet obligatoire"
                    return@setOnClickListener
                }
                d.dismiss()
                val rev = depot.valider(item, texteObjet)
                afficher()
                genererPdf(rev.num)
            }
        }
        d.show()
    }

    private fun genererPdf(num: Int) {
        val donnees = try {
            depot.donneesFiche(item, num)
        } catch (e: Exception) {
            message("PDF", "Impossible de préparer la fiche : ${e.message}")
            return
        }
        val fichier = depot.fichierPdf(item, num)
        val attente = attente("Génération du PDF…")
        Taches.lancer({ depot.genererPdf(donnees, fichier) }, { (local, copie) ->
            attente.dismiss()
            AlertDialog.Builder(this)
                .setTitle("PDF de la Rév. $num")
                .setMessage("PDF généré : ${local.name}\n\n" + (copie?.let { "Copié dans : $it" } ?: "Aucun dossier de travail choisi : PDF enregistré dans l'application uniquement."))
                .setPositiveButton("Voir le PDF") { _, _ -> afficherPdf(local, "${item.nom} — Rév. $num") }
                .setNegativeButton("Fermer", null)
                .show()
        }, { e ->
            attente.dismiss()
            message("PDF", "Génération impossible : ${e.message}")
        })
    }

    private fun choisirPdf() {
        val revs = item.revisions.sortedByDescending { it.num }
        if (revs.isEmpty()) {
            message("PDF", "Aucune révision validée pour ${item.nom}. Touchez « VALIDER » pour générer la Rév. 0.")
            return
        }
        val libelles = revs.map { r ->
            val f = depot.fichierPdf(item, r.num)
            "Rév. ${r.num} — ${r.date} — ${r.objet}" + if (f.exists()) "" else "  (à régénérer)"
        }
        AlertDialog.Builder(this)
            .setTitle("PDF de ${item.nom}")
            .setItems(libelles.toTypedArray()) { _, i ->
                val r = revs[i]
                val f = depot.fichierPdf(item, r.num)
                if (f.exists()) afficherPdf(f, "${item.nom} — Rév. ${r.num}") else genererPdf(r.num)
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    companion object {
        const val EXTRA_ITEM = "item"
        private const val REQ_PHOTO = 10
        private const val REQ_FICHIER_PHOTO = 11
        private const val REQ_FICHIER_PLAN = 12
    }
}
