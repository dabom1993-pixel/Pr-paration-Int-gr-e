package com.adf.pirobinetterie.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.adf.pirobinetterie.PiApp
import com.adf.pirobinetterie.R
import com.adf.pirobinetterie.data.Depot
import com.adf.pirobinetterie.data.Dossier
import com.adf.pirobinetterie.data.Taches
import com.adf.pirobinetterie.model.ChampsBride
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.Statut
import com.adf.pirobinetterie.update.MiseAJour
import java.io.File

/**
 * Écran principal : dossier de travail, import du fichier Excel + photos, liste des items avec
 * leur statut, export Excel.
 */
class MainActivity : Activity() {

    private val depot: Depot get() = (application as PiApp).depot

    private lateinit var infoProjet: TextView
    private lateinit var resume: TextView
    private lateinit var recherche: EditText
    private lateinit var liste: ListView
    private lateinit var vide: TextView
    private val filtres = mutableMapOf<String, TextView>()
    private var filtre = "Tous"
    private val adaptateur = Adaptateur()

    /** Import à lancer juste après le choix du dossier de travail. */
    private var importerApresChoix = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(construire())
        if (savedInstanceState == null) verifierMiseAJourAuDemarrage()
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
    }

    // --- Construction de l'écran ----------------------------------------------------------

    private fun construire(): View {
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Couleurs.FOND)
        }

        val barre = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Couleurs.MARINE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_adf)
            adjustViewBounds = true
            background = fondArrondi(Color.WHITE, dp(6).toFloat())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener { verifierMiseAJour(manuel = true) }
        }
        barre.addView(logo, lp(WRAP, dp(56)))
        barre.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            addView(texte("Préparation intégrée — Robinetterie", 22f, true, Color.WHITE))
            infoProjet = texte("", 15f, false, 0xFFCFD8E6.toInt())
            addView(infoProjet)
        }, lp(0, WRAP, 1f))
        barre.addView(bouton("Importer", Couleurs.VERT) { importer() }, lp(WRAP, WRAP).marges(dp(8), 0, 0, 0))
        barre.addView(bouton("Exporter Excel", Couleurs.MARINE_CLAIR) { exporterExcel() }, lp(WRAP, WRAP).marges(dp(8), 0, 0, 0))
        barre.addView(bouton("Réglages", Couleurs.MARINE_CLAIR) { reglages() }, lp(WRAP, WRAP).marges(dp(8), 0, 0, 0))
        racine.addView(barre, lp(MATCH, WRAP))

        // Projet : Client / Lieu / Unité / Année (modifiables) + résumé des statuts.
        val bandeau = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(4))
        }
        resume = texte("", 16f, false, Couleurs.TEXTE)
        bandeau.addView(resume, lp(0, WRAP, 1f))
        bandeau.addView(bouton("En-tête du projet", Couleurs.MARINE) { modifierEnTete() })
        racine.addView(bandeau, lp(MATCH, WRAP))

        // Recherche + filtres par statut.
        val barreFiltres = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        recherche = EditText(this).apply {
            hint = "Rechercher un item, une unité, un type…"
            textSize = 17f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = fondArrondi(Color.WHITE, dp(8).toFloat(), Couleurs.BORDURE, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = adaptateur.filtrer()
            })
        }
        barreFiltres.addView(recherche, lp(0, WRAP, 1f))
        for (f in listOf("Tous", "À valider", "En cours", "Validés")) {
            val t = texte(f, 16f, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener {
                    filtre = f
                    majFiltres()
                    adaptateur.filtrer()
                }
            }
            filtres[f] = t
            barreFiltres.addView(t, lp(WRAP, WRAP).marges(dp(8), 0, 0, 0))
        }
        majFiltres()
        racine.addView(barreFiltres, lp(MATCH, WRAP))

        val zoneListe = android.widget.FrameLayout(this)
        liste = ListView(this).apply {
            adapter = adaptateur
            divider = null
            dividerHeight = 0
            setPadding(dp(12), 0, dp(12), dp(12))
            clipToPadding = false
            setOnItemClickListener { _, _, position, _ ->
                val item = adaptateur.getItem(position)
                startActivity(Intent(this@MainActivity, FicheActivity::class.java).putExtra(FicheActivity.EXTRA_ITEM, item.nom))
            }
        }
        vide = texte("", 18f, false, Couleurs.GRIS).apply {
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(40), dp(40), dp(40))
        }
        zoneListe.addView(liste, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        zoneListe.addView(vide, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        liste.emptyView = vide
        racine.addView(zoneListe, lp(MATCH, 0, 1f))
        return racine
    }

    private fun majFiltres() {
        for ((nom, t) in filtres) {
            val actif = nom == filtre
            t.setTextColor(if (actif) Color.WHITE else Couleurs.MARINE)
            t.background = fondArrondi(if (actif) Couleurs.MARINE else Color.WHITE, dp(20).toFloat(), Couleurs.MARINE, dp(1))
        }
    }

    private fun rafraichir() {
        val p = depot.projet
        val dossier = depot.arbre?.let { Dossier.libelle(it) } ?: "aucun dossier choisi"
        if (p == null) {
            infoProjet.text = "Dossier de travail : $dossier"
            resume.text = "Aucun projet importé"
            vide.text = "Aucun projet.\n\nCopiez par câble USB, sur la tablette, un dossier (ex. Documents/PI_Robinetterie) contenant :\n" +
                "  • Import/  →  le fichier Excel (.xlsm)\n" +
                "  • Import/Photos/  →  « Rob 01_photo.jpg », « Rob 01_plan.jpg »…\n" +
                "  • Import/logo_client.png (facultatif)\n\n" +
                "Puis touchez « Importer » et choisissez ce dossier."
        } else {
            val e = p.enTete
            infoProjet.text = listOf(e.client, e.lieu, e.unite, e.annee).filter { it.isNotBlank() }.joinToString("  ·  ")
                .ifEmpty { "Client / Lieu non renseignés" } + "     —     ${p.fichierSource}"
            val parStatut = p.items.groupingBy { it.statut() }.eachCount()
            resume.text = "${p.items.size} items  ·  ${parStatut[Statut.VALIDE] ?: 0} validés  ·  " +
                "${(parStatut[Statut.EN_COURS] ?: 0) + (parStatut[Statut.REVISION_EN_COURS] ?: 0)} en cours  ·  " +
                "${parStatut[Statut.A_VALIDER] ?: 0} à valider      Dossier : $dossier"
            vide.text = "Aucun item ne correspond au filtre."
        }
        adaptateur.filtrer()
    }

    // --- Liste des items ------------------------------------------------------------------

    private inner class Adaptateur : BaseAdapter() {
        private var items: List<Item> = emptyList()

        fun filtrer() {
            val p = depot.projet
            val q = if (::recherche.isInitialized) recherche.text.toString().trim().lowercase() else ""
            items = p?.items.orEmpty().filter { item ->
                val statutOk = when (filtre) {
                    "À valider" -> item.statut() == Statut.A_VALIDER
                    "En cours" -> item.statut() == Statut.EN_COURS || item.statut() == Statut.REVISION_EN_COURS
                    "Validés" -> item.statut() == Statut.VALIDE
                    else -> true
                }
                statutOk && (q.isEmpty() || listOf(item.nom, item[ChampsFiche.UNITE], item[ChampsFiche.TYPE], item["travaux"])
                    .any { it.lowercase().contains(q) })
            }
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val ligne = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = fondCliquable(Color.WHITE, 0xFFE3EAF4.toInt(), dp(10).toFloat(), Couleurs.BORDURE, dp(1))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = android.widget.AbsListView.LayoutParams(MATCH, WRAP)
            }
            val gauche = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            gauche.addView(texte(item.nom, 21f, true, Couleurs.MARINE))
            val details = listOf(item[ChampsFiche.UNITE], item[ChampsFiche.TYPE], item["travaux"]).filter { it.isNotBlank() }
            val nbBrides = item.brides.size
            gauche.addView(texte((details + "$nbBrides bride${if (nbBrides > 1) "s" else ""}").joinToString("  ·  "), 15f, false, Couleurs.GRIS))
            ligne.addView(gauche, lp(0, WRAP, 1f))

            val manques = listOfNotNull(if (item.photo.isEmpty()) "photo" else null, if (item.plan.isEmpty()) "plot plan" else null)
            if (manques.isNotEmpty()) ligne.addView(texte("Sans ${manques.joinToString(" / ")}", 14f, false, Couleurs.ORANGE), lp(WRAP, WRAP).marges(0, 0, dp(16), 0))

            val (libelle, couleur) = when (item.statut()) {
                Statut.A_VALIDER -> "À valider" to Couleurs.GRIS
                Statut.EN_COURS -> "Rév. 0 en cours" to Couleurs.ORANGE
                Statut.REVISION_EN_COURS -> "Rév. ${item.numEnCours} en cours" to Couleurs.ORANGE
                Statut.VALIDE -> "Validé — Rév. ${item.derniere?.num}" to Couleurs.VERT
            }
            ligne.addView(badge(libelle, couleur))
            return LinearLayout(this@MainActivity).apply {
                setPadding(0, dp(4), 0, dp(4))
                addView(ligne, lp(MATCH, WRAP))
            }
        }
    }

    // --- Dossier de travail et import -----------------------------------------------------

    private fun choisirDossier() {
        message(
            "Dossier de travail",
            "Choisissez le dossier copié sur la tablette (ex. Documents/PI_Robinetterie), puis touchez « Utiliser ce dossier ».\n\n" +
                "L'application y lira Import/ et y écrira Export/ (PDF et Excel)."
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(intent, REQ_DOSSIER)
        }
    }

    @Deprecated("API framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_DOSSIER) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            importerApresChoix = false
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        depot.arbre = uri
        rafraichir()
        if (importerApresChoix) {
            importerApresChoix = false
            importer()
        }
    }

    private fun importer() {
        if (depot.racine() == null) {
            importerApresChoix = true
            choisirDossier()
            return
        }
        val fichiers = try {
            depot.fichiersExcel()
        } catch (e: Exception) {
            importerApresChoix = true
            choisirDossier()
            return
        }
        when {
            fichiers.isEmpty() -> AlertDialog.Builder(this)
                .setTitle("Aucun fichier Excel")
                .setMessage("Aucun fichier .xlsm / .xlsx trouvé dans ${depot.arbre?.let { Dossier.libelle(it) }}/Import.\n\nCopiez-le par câble USB puis réessayez, ou choisissez un autre dossier.")
                .setPositiveButton("Choisir un autre dossier") { _, _ -> importerApresChoix = true; choisirDossier() }
                .setNegativeButton("Fermer", null)
                .show()
            fichiers.size == 1 -> confirmerImport(fichiers[0])
            else -> AlertDialog.Builder(this)
                .setTitle("Quel fichier importer ?")
                .setItems(fichiers.map { it.nom }.toTypedArray()) { _, i -> confirmerImport(fichiers[i]) }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun confirmerImport(f: Dossier.Fichier) {
        val p = depot.projet
        val revs = p?.items?.sumOf { it.revisions.size } ?: 0
        val avertissement = if (p == null) "" else
            "\n\nAttention : le projet en cours (${p.items.size} items, $revs révision(s) validée(s)) sera remplacé. " +
                "Les PDF déjà générés restent dans Export/PDF. Pensez à exporter l'Excel avant."
        confirmer("Importer ${f.nom} ?", "Le fichier et les photos du dossier Import/Photos seront chargés dans la tablette.$avertissement", "Importer") {
            val attente = attente("Import en cours…")
            Taches.lancer({ depot.importer(f) }, { r ->
                attente.dismiss()
                rafraichir()
                val manques = StringBuilder()
                if (r.sansPhoto.isNotEmpty()) manques.append("\n\nSans photo : ${r.sansPhoto.take(15).joinToString(", ")}${if (r.sansPhoto.size > 15) "…" else ""}")
                if (r.sansPlan.isNotEmpty()) manques.append("\nSans plot plan : ${r.sansPlan.take(15).joinToString(", ")}${if (r.sansPlan.size > 15) "…" else ""}")
                message("Import terminé", "${r.items} items, ${r.brides} brides.\n${r.photos} photos et ${r.plans} plots plans trouvés.$manques")
            }, { e ->
                attente.dismiss()
                message("Import impossible", e.message ?: e.toString())
            })
        }
    }

    // --- Export Excel ---------------------------------------------------------------------

    private fun exporterExcel() {
        val p = depot.projet ?: run { message("Export Excel", "Aucun projet importé."); return }
        val nonValides = p.items.filter { it.modifieDepuisValidation() }
        val lancer = {
            val copie = depot.copieProjet()!!
            val attente = attente("Export Excel en cours…")
            Taches.lancer({ depot.exporterExcel(copie) }, { chemin ->
                attente.dismiss()
                message("Export Excel terminé", "Fichier créé :\n$chemin\n\nSuivi : une ligne par item (colonne Rév = dernière révision).\nMatos : une ligne par bride.\nFond jaune : modifications de la dernière révision.")
            }, { e ->
                attente.dismiss()
                message("Export impossible", e.message ?: e.toString())
            })
        }
        if (depot.racine() == null) {
            confirmer("Dossier de travail", "Aucun dossier de travail : le fichier sera seulement enregistré dans l'application. Choisir un dossier d'abord ?", "Choisir") { choisirDossier() }
            return
        }
        if (nonValides.isNotEmpty()) {
            confirmer(
                "Modifications non validées",
                "${nonValides.size} item(s) ont des modifications non validées (${nonValides.take(10).joinToString(", ") { it.nom }}${if (nonValides.size > 10) "…" else ""}).\n\n" +
                    "L'Excel ne contient que les révisions validées. Exporter quand même ?",
                "Exporter"
            ) { lancer() }
        } else lancer()
    }

    // --- En-tête et réglages --------------------------------------------------------------

    private fun modifierEnTete() {
        val p = depot.projet ?: run { message("En-tête", "Aucun projet importé."); return }
        val e = p.enTete
        val champs = listOf("Client" to e.client, "Lieu" to e.lieu, "Unité" to e.unite, "Année" to e.annee)
        val contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val editions = champs.map { (libelle, valeur) ->
            contenu.addView(texte(libelle, 14f, true, Couleurs.GRIS))
            EditText(this).apply {
                setText(valeur)
                textSize = 19f
                setSingleLine(true)
                contenu.addView(this, lp(MATCH, WRAP).marges(0, 0, 0, dp(8)))
            }
        }
        AlertDialog.Builder(this).setTitle("En-tête du projet (titre des fiches)")
            .setView(contenu)
            .setPositiveButton("OK") { _, _ ->
                e.client = editions[0].text.toString().trim()
                e.lieu = editions[1].text.toString().trim()
                e.unite = editions[2].text.toString().trim()
                e.annee = editions[3].text.toString().trim()
                depot.sauver()
                rafraichir()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun reglages() {
        val contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        contenu.addView(texte("Longueur ajoutée aux tiges si Rondelle = Oui (mm)", 15f, true, Couleurs.GRIS))
        val rondelle = EditText(this).apply {
            setText(com.adf.pirobinetterie.model.Abaque.nombre(depot.rondelleMm))
            textSize = 19f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        contenu.addView(rondelle, lp(MATCH, WRAP).marges(0, 0, 0, dp(16)))
        contenu.addView(texte("Dossier de travail", 15f, true, Couleurs.GRIS))
        contenu.addView(texte(depot.arbre?.let { Dossier.libelle(it) } ?: "aucun", 17f))
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        contenu.addView(texte("\nVersion de l'application : $version\nToucher le logo ADF pour chercher une mise à jour.", 14f, false, Couleurs.GRIS))
        AlertDialog.Builder(this).setTitle("Réglages")
            .setView(contenu)
            .setPositiveButton("Enregistrer") { _, _ ->
                rondelle.text.toString().replace(',', '.').toDoubleOrNull()?.let { depot.rondelleMm = it }
            }
            .setNeutralButton("Changer de dossier") { _, _ -> choisirDossier() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // --- Mise à jour de l'application (logo ADF) ------------------------------------------

    private fun verifierMiseAJourAuDemarrage() {
        if (!MiseAJour.reseauDisponible(this)) return
        Taches.lancer({ MiseAJour.derniereVersion() }, { info ->
            if (info != null && MiseAJour.estNouvelle(this, info)) proposerMiseAJour(info)
        }, { })
    }

    private fun verifierMiseAJour(manuel: Boolean) {
        if (!MiseAJour.reseauDisponible(this)) {
            if (manuel) message("Mise à jour", "Pas de connexion internet (Wifi ou données mobiles) : impossible de vérifier.")
            return
        }
        val attente = attente("Recherche d'une mise à jour…")
        Taches.lancer({ MiseAJour.derniereVersion() }, { info ->
            attente.dismiss()
            when {
                info == null -> message("Mise à jour", "Aucune version publiée trouvée (lancer « Build APK » sur GitHub).")
                MiseAJour.estNouvelle(this, info) -> proposerMiseAJour(info)
                else -> message("Mise à jour", "Pas de mise à jour nécessaire : vous disposez de la dernière version.")
            }
        }, { e ->
            attente.dismiss()
            message("Mise à jour", "Vérification impossible : ${e.message}")
        })
    }

    private fun proposerMiseAJour(info: MiseAJour.Info) {
        confirmer("Mise à jour disponible", "Une nouvelle version de l'application est disponible. La télécharger et l'installer ?\n\nLes données de la tablette sont conservées.", "Installer") {
            val barre = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            val vue = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(12))
                addView(texte("Téléchargement…", 16f))
                addView(barre, lp(MATCH, WRAP).marges(0, dp(12), 0, 0))
            }
            val d = AlertDialog.Builder(this).setView(vue).setCancelable(false).show()
            Taches.lancer({
                MiseAJour.telecharger(this, info) { pct -> runOnUiThread { barre.progress = pct } }
            }, { apk ->
                d.dismiss()
                installer(info, apk)
            }, { e ->
                d.dismiss()
                message("Mise à jour", "Téléchargement impossible : ${e.message}")
            })
        }
    }

    private fun installer(info: MiseAJour.Info, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            message("Autorisation nécessaire", "Autorisez « PI Robinetterie » à installer des applications, puis touchez à nouveau le logo ADF.") {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
            return
        }
        depot.copieDeSecours()
        MiseAJour.marquerEnAttente(this, info)
        startActivity(MiseAJour.intentInstallation(this, apk))
    }

    companion object {
        private const val REQ_DOSSIER = 1
    }
}
