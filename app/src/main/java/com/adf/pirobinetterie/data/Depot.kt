package com.adf.pirobinetterie.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.adf.pirobinetterie.R
import com.adf.pirobinetterie.excel.ExcelExporter
import com.adf.pirobinetterie.excel.ExcelImporter
import com.adf.pirobinetterie.excel.PlotPlanImporter
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.Listes
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.Revision
import com.adf.pirobinetterie.model.cle
import com.adf.pirobinetterie.pdf.DonneesFiche
import com.adf.pirobinetterie.pdf.PdfFiche
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Point central des données : projet en cours (sauvegardé en JSON dans l'app), dossier de
 * travail sur la tablette (Import / Export), photos, PDF et export Excel.
 */
class Depot(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("pi_robinetterie", Context.MODE_PRIVATE)
    private val fichierProjet = File(ctx.filesDir, "projet.json")

    /** Copie du classeur importé : base de chaque export Excel (l'original n'est jamais modifié). */
    val fichierSource = File(ctx.filesDir, "source.xlsm")

    @Volatile
    var projet: Projet? = null
        private set

    private val fichierPlotPlan = File(ctx.filesDir, "plotplan.json")

    /** Plot plan importé (fichier Excel PlotPlan), null tant qu'il n'a pas été importé. */
    @Volatile
    var plotPlan: PlotPlan? = null
        private set

    init {
        if (fichierProjet.exists()) {
            projet = try {
                ProjetJson.lire(fichierProjet.readText())
            } catch (_: Exception) {
                null
            }
        }
        if (fichierPlotPlan.exists()) {
            plotPlan = try {
                PlotPlan.lire(fichierPlotPlan.readText())
            } catch (_: Exception) {
                null
            }
        }
    }

    // --- Réglages -------------------------------------------------------------------------

    /** Dossier de travail choisi sur la tablette (contient Import/ et Export/). */
    var arbre: Uri?
        get() = prefs.getString("arbre", null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString("arbre", value?.toString()).apply()

    /** Longueur ajoutée aux tiges quand la bride a des rondelles (mm). */
    var rondelleMm: Double
        get() = prefs.getFloat("rondelle_mm", Float.NaN).toDouble().takeIf { !it.isNaN() } ?: rondelleParDefaut()
        set(value) = prefs.edit().putFloat("rondelle_mm", value.toFloat()).apply()

    private fun rondelleParDefaut(): Double =
        projet?.listes?.get(Listes.RONDELLE)?.firstNotNullOfOrNull { it.replace(',', '.').toDoubleOrNull() } ?: 10.0

    fun racine(): Dossier? = arbre?.let {
        try {
            Dossier.racine(ctx.contentResolver, it)
        } catch (_: Exception) {
            null
        }
    }

    /** Dossier "Import" du dossier de travail (ou le dossier de travail lui-même). */
    fun dossierImport(): Dossier? {
        val r = racine() ?: return null
        return r.sousDossier("Import", false) ?: r
    }

    // --- Sauvegarde -----------------------------------------------------------------------

    /** Sauvegarde immédiate de l'état (texte préparé ici, écriture du fichier en arrière-plan). */
    fun sauver() {
        val p = projet ?: return
        val texte = ProjetJson.ecrire(p)
        Taches.enFond {
            val tmp = File(fichierProjet.parentFile, "projet.json.tmp")
            tmp.writeText(texte)
            if (!tmp.renameTo(fichierProjet)) {
                fichierProjet.writeText(texte)
                tmp.delete()
            }
        }
    }

    /** Copie de secours de la sauvegarde (avant un nouvel import ou une mise à jour de l'app). */
    fun copieDeSecours() {
        if (!fichierProjet.exists()) return
        val dir = File(ctx.filesDir, "secours").apply { mkdirs() }
        fichierProjet.copyTo(File(dir, "projet_${horodatage()}.json"), overwrite = true)
    }

    // --- Import ---------------------------------------------------------------------------

    fun fichiersExcel(): List<Dossier.Fichier> = dossierImport()?.contenu().orEmpty()
        .filter { !it.estDossier && !it.nom.startsWith("~$") }
        .filter { it.nom.lowercase().let { n -> n.endsWith(".xlsm") || n.endsWith(".xlsx") } }
        .sortedBy { it.nom.lowercase() }

    class RapportImport(val items: Int, val brides: Int, val photos: Int, val plans: Int, val sansPhoto: List<String>, val sansPlan: List<String>)

    /** À exécuter en arrière-plan (voir [Taches]). */
    fun importer(excel: Dossier.Fichier): RapportImport {
        val imp = dossierImport() ?: throw IllegalStateException("Dossier de travail inaccessible : choisissez-le à nouveau.")
        val tmp = File(ctx.cacheDir, "import.xlsm")
        imp.copierVers(excel, tmp)

        // Images : dossier Import/Photos (et Import/ lui-même). Nom sans extension normalisé -> fichier.
        val images = linkedMapOf<String, Dossier.Fichier>()
        val sources = listOfNotNull(imp.sousDossier("Photos", false), imp)
        for (d in sources) for (f in d.contenu()) {
            val ext = f.nom.substringAfterLast('.', "").lowercase()
            if (!f.estDossier && ext in EXTENSIONS_IMAGES) images.putIfAbsent(cle(f.nom.substringBeforeLast('.')), f)
        }

        val dirPhotos = dossierPhotos(ctx).apply { mkdirs() }
        val h = horodatage()
        fun copier(f: Dossier.Fichier?, nom: String, genre: String): String {
            f ?: return ""
            val ext = f.nom.substringAfterLast('.', "jpg").lowercase()
            val dest = File(dirPhotos, "${nomFichier(nom)}_${genre}_$h.$ext")
            imp.copierVers(f, dest)
            return dest.absolutePath
        }

        val nouveau = ExcelImporter.importer(tmp, excel.nom) { nom ->
            val photo = images[cle(nom + "_photo")] ?: images[cle(nom)]
            val plan = SUFFIXES_PLAN.firstNotNullOfOrNull { images[cle(nom + "_" + it)] }
            copier(photo, nom, "photo") to copier(plan, nom, "plan")
        }

        // Logo client facultatif : Import/logo_client.png (ou .jpg).
        File(ctx.filesDir, "logo_client.png").delete()
        images[cle("logo_client")]?.let { f ->
            val dest = File(ctx.filesDir, "logo_client_source")
            imp.copierVers(f, dest)
            BitmapFactory.decodeFile(dest.absolutePath)?.let { bmp ->
                File(ctx.filesDir, "logo_client.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            dest.delete()
        }

        // Localisation : le plot plan importé remplace l'image "_plan" du dossier Photos.
        plotPlan?.let { PlotPlan.appliquer(it, nouveau, dossierLocalisations(ctx), h) }

        copieDeSecours()
        tmp.copyTo(fichierSource, overwrite = true)
        tmp.delete()
        projet = nouveau
        fichierProjet.writeText(ProjetJson.ecrire(nouveau))

        return RapportImport(
            items = nouveau.items.size,
            brides = nouveau.items.sumOf { it.brides.size },
            photos = nouveau.items.count { it.photo.isNotEmpty() },
            plans = nouveau.items.count { it.plan.isNotEmpty() },
            sansPhoto = nouveau.items.filter { it.photo.isEmpty() }.map { it.nom },
            sansPlan = nouveau.items.filter { it.plan.isEmpty() }.map { it.nom }
        )
    }

    // --- Plot plan -------------------------------------------------------------------------

    class RapportPlotPlan(val plans: List<String>, val points: Int, val localises: Int, val sansPoint: List<String>, val sansPosition: Int)

    /**
     * Importe le fichier Excel PlotPlan (plans + points des équipements) et génère pour chaque
     * item du projet son image de localisation. À exécuter en arrière-plan.
     */
    fun importerPlotPlan(excel: Dossier.Fichier): RapportPlotPlan {
        val imp = dossierImport() ?: throw IllegalStateException("Dossier de travail inaccessible : choisissez-le à nouveau.")
        val tmp = File(ctx.cacheDir, "plotplan.xlsm")
        imp.copierVers(excel, tmp)
        try {
            val lu = PlotPlanImporter.lire(tmp)
            if (lu.plans.isEmpty()) throw PlotPlanImporter.PlotPlanException("Aucune image de plan trouvée dans ${excel.nom}.")
            val h = horodatage()
            val dir = File(ctx.filesDir, "plotplan").apply { mkdirs() }
            val plans = linkedMapOf<String, String>()
            for (pl in lu.plans) {
                val dest = File(dir, "plan_${nomFichier(pl.unite)}_$h.${pl.image.substringAfterLast('.', "jpg")}")
                PlotPlanImporter.extraireImage(tmp, pl.image, dest)
                plans[pl.unite] = dest.absolutePath
            }
            val plot = PlotPlan(excel.nom, java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date()), plans, lu.points)
            fichierPlotPlan.writeText(plot.json())
            plotPlan = plot

            val p = projet
            var localises = 0
            if (p != null) {
                localises = PlotPlan.appliquer(plot, p, dossierLocalisations(ctx), h)
                fichierProjet.writeText(ProjetJson.ecrire(p))
            }
            return RapportPlotPlan(
                plans = plans.keys.toList(),
                points = lu.points.size,
                localises = localises,
                sansPoint = p?.items?.filter { plot.pointsDe(it.nom).isEmpty() }?.map { it.nom }.orEmpty(),
                sansPosition = lu.sansPosition.size
            )
        } finally {
            tmp.delete()
        }
    }

    // --- Validation / PDF -----------------------------------------------------------------

    /** Fige la révision en cours de [item] (à appeler depuis l'écran), puis sauvegarde. */
    fun valider(item: Item, objet: String): Revision {
        val rev = Revision(item.numEnCours, SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date()), objet.trim(), item.etatCourant())
        item.revisions.add(rev)
        sauver()
        return rev
    }

    fun logoAdf(): File {
        val f = File(ctx.filesDir, "logo_adf.png")
        if (!f.exists()) {
            val bmp = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_adf, BitmapFactory.Options().apply { inScaled = false })
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return f
    }

    fun logoClient(): File? = File(ctx.filesDir, "logo_client.png").takeIf { it.exists() }

    fun fichierPdf(item: Item, num: Int): File = File(dossierPdf(ctx), "${nomFichier(item.nom)}_Rev$num.pdf")

    /** Données de la fiche (à préparer sur l'écran), puis [genererPdf] en arrière-plan. */
    fun donneesFiche(item: Item, num: Int): DonneesFiche {
        val p = projet ?: throw IllegalStateException("Aucun projet")
        return DonneesFiche.pour(p, item, num, logoAdf().absolutePath, logoClient()?.absolutePath)
    }

    /**
     * Génère le PDF (en arrière-plan) et le copie dans Export/PDF du dossier de travail.
     * Retourne le fichier local et le chemin affiché de la copie (null si pas de dossier choisi).
     */
    fun genererPdf(d: DonneesFiche, fichier: File): Pair<File, String?> {
        PdfFiche.generer(d, fichier)
        val copie = racine()?.sousDossier("Export", true)?.sousDossier("PDF", true)?.let { dir ->
            dir.ecrire(fichier.name, "application/pdf", fichier)
            "${arbre?.let { Dossier.libelle(it) }}/Export/PDF/${fichier.name}"
        }
        return fichier to copie
    }

    fun pdfsDe(item: Item): List<Pair<Int, File>> = item.revisions.sortedByDescending { it.num }
        .map { it.num to fichierPdf(item, it.num) }
        .filter { it.second.exists() }

    // --- Export Excel ---------------------------------------------------------------------

    /** À exécuter en arrière-plan. [copie] = copie du projet faite depuis l'écran. Retourne le chemin affiché. */
    fun exporterExcel(copie: Projet): String {
        if (!fichierSource.exists()) throw IllegalStateException("Fichier Excel importé introuvable : refaites un import.")
        val base = copie.fichierSource.substringBeforeLast('.').ifBlank { "PI_Robinetterie" }
        val ext = copie.fichierSource.substringAfterLast('.', "xlsm").lowercase().takeIf { it == "xlsx" || it == "xlsm" } ?: "xlsm"
        val nom = "${nomFichier(base)}_MAJ_${horodatage()}.$ext"
        val local = File(ctx.getExternalFilesDir("exports") ?: File(ctx.filesDir, "exports").apply { mkdirs() }, nom)
        ExcelExporter.exporter(fichierSource, copie, local)
        val dir = racine()?.sousDossier("Export", true)?.sousDossier("Excel", true)
            ?: return local.absolutePath
        // Type générique : garantit qu'Android conserve l'extension .xlsm/.xlsx du nom choisi.
        dir.ecrire(nom, "application/octet-stream", local)
        return "${arbre?.let { Dossier.libelle(it) }}/Export/Excel/$nom"
    }

    /** Copie indépendante du projet (pour l'exporter en arrière-plan pendant que l'on continue à saisir). */
    fun copieProjet(): Projet? = projet?.let { ProjetJson.lire(ProjetJson.ecrire(it)) }

    /** Nouveau fichier photo pour l'appareil photo. */
    fun nouvellePhoto(item: Item, genre: String): File =
        File(dossierPhotos(ctx).apply { mkdirs() }, "${nomFichier(item.nom)}_${genre}_${horodatage()}.jpg")

    companion object {
        private val EXTENSIONS_IMAGES = setOf("jpg", "jpeg", "png", "webp")
        private val SUFFIXES_PLAN = listOf("plan", "plotplan", "plot", "localisation")

        fun dossierPhotos(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "photos")
        fun dossierPdf(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "pdf")
        fun dossierLocalisations(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "localisations")

        fun horodatage(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())

        /** Caractères interdits dans un nom de fichier remplacés. */
        fun nomFichier(nom: String): String = nom.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "item" }
    }
}
