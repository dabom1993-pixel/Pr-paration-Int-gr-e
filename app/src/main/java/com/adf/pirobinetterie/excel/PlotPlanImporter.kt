package com.adf.pirobinetterie.excel

import com.adf.pirobinetterie.model.cle
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Lecture du classeur "PlotPlan" (localisation des interventions sur les plots plans) :
 * - onglet Plans : une ligne par plan (Unité -> onglet contenant l'image du plan) ;
 * - onglet Interventions : Nom Plan, Famille, Équipement / TAG, Repère, X plan, Y plan, Forme,
 *   Largeur, Hauteur (positions en fractions 0-1 de l'image, comme les macros du fichier) ;
 * - onglet Paramètres : couleur de chaque famille (= couleur de fond de sa cellule), taille des
 *   points, nombre de colonnes du plan (repérage par cellule).
 * Les légendes et traits des macros ne sont pas repris : seulement le point (rond ou carré).
 */
object PlotPlanImporter {

    class PlotPlanException(message: String) : Exception(message)

    /** Un plan : unité + image (chemin dans l'archive) + taille affichée dans Excel (points). */
    class Plan(val unite: String, val image: String, val largeurPt: Double, val hauteurPt: Double)

    /** Un point sur un plan. Position et taille en fractions de l'image (centre du point). */
    class Point(
        val tag: String, val unite: String, val famille: String,
        val fx: Double, val fy: Double, val fw: Double, val fh: Double,
        val carre: Boolean, val couleur: Int, val etire: Boolean
    )

    class Resultat(val plans: List<Plan>, val points: List<Point>, val sansPosition: List<String>)

    private const val ONGLET_INTERVENTIONS = "Interventions"
    private const val ONGLET_PLANS = "Plans"
    private const val ONGLET_PARAMETRES = "Paramètres"
    private const val EMU_PAR_POINT = 12700.0
    private const val PLAN_COL0 = 2 // colonne B
    private const val PLAN_LIG0 = 3 // ligne 3
    private const val GRIS = 0xFF6E6E6E.toInt()

    fun lire(fichier: File): Resultat {
        Classeur(fichier).use { c ->
            val interv = c.lireOnglet(ONGLET_INTERVENTIONS)
                ?: throw PlotPlanException("Onglet \"$ONGLET_INTERVENTIONS\" introuvable : ce n'est pas un fichier PlotPlan.")
            val plansFeuille = c.lireOnglet(ONGLET_PLANS)
                ?: throw PlotPlanException("Onglet \"$ONGLET_PLANS\" introuvable : ce n'est pas un fichier PlotPlan.")
            val params = c.lireOnglet(ONGLET_PARAMETRES)

            ZipFile(fichier).use { zip ->
                // Plans : Unité -> onglet -> image.
                val plans = mutableListOf<Plan>()
                val (lp, colsP) = enTetes(plansFeuille, setOf("unite", "feuille"))
                    ?: throw PlotPlanException("Tableau des plans introuvable (colonnes Unité / Feuille).")
                for ((l, cellules) in plansFeuille.lignes) {
                    if (l <= lp) continue
                    val unite = cellules[colsP["unite"]]?.trim().orEmpty()
                    val onglet = cellules[colsP["feuille"]]?.trim().orEmpty()
                    if (unite.isEmpty() || onglet.isEmpty()) continue
                    val chemin = c.cheminOnglet(onglet) ?: continue
                    imagePlan(zip, chemin)?.let { (img, w, h) -> plans.add(Plan(unite, img, w, h)) }
                }

                // Paramètres : taille des points, colonnes du plan, couleurs des familles.
                var taille = 0.012
                var nbCol = 60
                params?.lignes?.values?.forEach { cellules ->
                    val k = cle(cellules[1].orEmpty())
                    val v = cellules[2]?.replace(',', '.')?.toDoubleOrNull()
                    if (v != null && k.startsWith("tailledespoints")) taille = v
                    if (v != null && k.startsWith("nombredecolonnesduplan")) nbCol = v.toInt().coerceIn(10, 300)
                }
                val couleurs = params?.let { couleursFamilles(zip, c, it) }.orEmpty()

                // Interventions.
                val (li, cols) = enTetes(interv, setOf("nomplan", "famille", "id"))
                    ?: throw PlotPlanException("Tableau des interventions introuvable (colonnes Nom Plan / Famille / ID).")
                val colTag = cols.entries.firstOrNull { it.key.startsWith("equipement") }?.value
                val points = mutableListOf<Point>()
                val sansPosition = mutableListOf<String>()
                for ((l, cellules) in interv.lignes) {
                    if (l <= li) continue
                    fun v(k: String) = cols[k]?.let { cellules[it] }?.trim().orEmpty()
                    val tag = colTag?.let { cellules[it] }?.trim().orEmpty()
                    val unite = v("nomplan")
                    if (tag.isEmpty() || unite.isEmpty()) continue
                    val plan = plans.firstOrNull { it.unite.equals(unite, ignoreCase = true) }
                    var fx = v("xplan").replace(',', '.').toDoubleOrNull()
                    var fy = v("yplan").replace(',', '.').toDoubleOrNull()
                    if (fx == null || fy == null || fx !in 0.0..1.0 || fy !in 0.0..1.0) {
                        val f = plan?.let { fracDepuisRepere(v("repere"), nbCol, it) }
                        fx = f?.first
                        fy = f?.second
                    }
                    if (fx == null || fy == null || plan == null) {
                        sansPosition.add(tag)
                        continue
                    }
                    // Taille : personnalisée (point étiré) ou par défaut (taille des points, 8 pt mini).
                    val d = maxOf(taille * plan.largeurPt, 8.0)
                    var mw = (v("largeur").replace(',', '.').toDoubleOrNull() ?: 0.0) * plan.largeurPt
                    var mh = (v("hauteur").replace(',', '.').toDoubleOrNull() ?: 0.0) * plan.hauteurPt
                    if (mw < 4 || mh < 4) {
                        mw = d
                        mh = d
                    }
                    val famille = v("famille")
                    points.add(
                        Point(
                            tag = tag, unite = plan.unite, famille = famille,
                            fx = fx, fy = fy, fw = mw / plan.largeurPt, fh = mh / plan.hauteurPt,
                            carre = cle(v("forme")).let { it.startsWith("ca") || it.startsWith("re") },
                            couleur = couleurs.entries.firstOrNull { it.key.equals(famille.trim(), ignoreCase = true) }?.value ?: GRIS,
                            etire = mw > d * 1.6 || mh > d * 1.6
                        )
                    )
                }
                return Resultat(plans, points, sansPosition)
            }
        }
    }

    /** Copie l'image d'un plan (chemin dans l'archive) vers [destination]. */
    fun extraireImage(fichier: File, image: String, destination: File) {
        ZipFile(fichier).use { zip ->
            val e = zip.getEntry(image) ?: throw PlotPlanException("Image du plan introuvable : $image")
            zip.getInputStream(e).use { i -> FileOutputStream(destination).use { o -> i.copyTo(o) } }
        }
    }

    // --- Outils ---------------------------------------------------------------------------

    /** Première ligne contenant toutes les clés [requis] : (ligne, clé -> colonne). */
    private fun enTetes(f: Feuille, requis: Set<String>): Pair<Int, Map<String, Int>>? {
        for ((l, cellules) in f.lignes) {
            if (l > 40) break
            val cols = linkedMapOf<String, Int>()
            for ((col, t) in cellules) cols.putIfAbsent(cle(t), col)
            if (requis.all { it in cols }) return l to cols
        }
        return null
    }

    /** "M24" -> centre de la cellule, en fractions du plan (même calcul que les macros). */
    private fun fracDepuisRepere(ref: String, nbCol: Int, plan: Plan): Pair<Double, Double>? {
        val s = ref.uppercase().replace(Regex("[ \\-$/._]"), "")
        if (!Regex("^[A-Z]+\\d+$").matches(s)) return null
        val (col, ligne) = Ref.decoupe(s)
        val nbLig = maxOf(1, Math.round(nbCol * plan.hauteurPt / plan.largeurPt).toInt())
        val ci = col - PLAN_COL0
        val ri = ligne - PLAN_LIG0
        if (ci < 0 || ci >= nbCol || ri < 0 || ri >= nbLig) return null
        return (ci + 0.5) / nbCol to (ri + 0.5) / nbLig
    }

    /** Image "PlotPlan" de l'onglet [cheminFeuille] : (chemin du média, largeur pt, hauteur pt). */
    private fun imagePlan(zip: ZipFile, cheminFeuille: String): Triple<String, Double, Double>? {
        val dessin = cibles(zip, cheminFeuille).entries.firstOrNull { it.value.first.endsWith("/drawing") }?.value?.second ?: return null
        val doc = zip.getEntry(dessin)?.let { e -> zip.getInputStream(e).use { ExcelExporter.parse(it.readBytes()) } } ?: return null
        val pics = doc.getElementsByTagName("xdr:pic")
        var choisi: Element? = null
        for (i in 0 until pics.length) {
            val p = pics.item(i) as Element
            val nom = (p.getElementsByTagName("xdr:cNvPr").item(0) as? Element)?.getAttribute("name").orEmpty()
            if (nom == "PlotPlan") { choisi = p; break }
            if (choisi == null && !nom.startsWith("MK_") && !nom.startsWith("LB_") && !nom.startsWith("UI_")) choisi = p
        }
        val pic = choisi ?: return null
        val rid = (pic.getElementsByTagName("a:blip").item(0) as? Element)?.getAttribute("r:embed") ?: return null
        val ext = pic.getElementsByTagName("a:ext").item(0) as? Element
        val w = (ext?.getAttribute("cx")?.toDoubleOrNull() ?: 0.0) / EMU_PAR_POINT
        val h = (ext?.getAttribute("cy")?.toDoubleOrNull() ?: 0.0) / EMU_PAR_POINT
        val media = cibles(zip, dessin)[rid]?.second ?: return null
        return Triple(media, if (w > 0) w else 540.0, if (h > 0) h else 380.0)
    }

    /** Relations d'une partie : Id -> (Type, chemin cible dans l'archive). */
    private fun cibles(zip: ZipFile, partie: String): Map<String, Pair<String, String>> {
        val dossier = partie.substringBeforeLast('/')
        val rels = zip.getEntry("$dossier/_rels/${partie.substringAfterLast('/')}.rels") ?: return emptyMap()
        val doc = zip.getInputStream(rels).use { ExcelExporter.parse(it.readBytes()) }
        val result = mutableMapOf<String, Pair<String, String>>()
        val liste = doc.getElementsByTagName("Relationship")
        for (i in 0 until liste.length) {
            val r = liste.item(i) as Element
            val cible = r.getAttribute("Target")
            val chemin = if (cible.startsWith("/")) cible.removePrefix("/") else normaliser("$dossier/$cible")
            result[r.getAttribute("Id")] = r.getAttribute("Type") to chemin
        }
        return result
    }

    private fun normaliser(chemin: String): String {
        val pile = ArrayDeque<String>()
        for (p in chemin.split('/')) when (p) {
            ".." -> if (pile.isNotEmpty()) pile.removeLast()
            ".", "" -> {}
            else -> pile.addLast(p)
        }
        return pile.joinToString("/")
    }

    // --- Couleurs des familles (fond des cellules de l'onglet Paramètres) -------------------

    private fun couleursFamilles(zip: ZipFile, c: Classeur, params: Feuille): Map<String, Int> {
        val (lh, cols) = enTetes(params, setOf("famille")) ?: return emptyMap()
        val col = cols.getValue("famille")
        val chemin = c.cheminOnglet(ONGLET_PARAMETRES) ?: return emptyMap()
        val feuille = zip.getInputStream(zip.getEntry(chemin)).use { ExcelExporter.parse(it.readBytes()) }
        val styleParRef = mutableMapOf<String, Int>()
        val cells = feuille.getElementsByTagName("c")
        for (i in 0 until cells.length) {
            val e = cells.item(i) as Element
            e.getAttribute("s").toIntOrNull()?.let { styleParRef[e.getAttribute("r")] = it }
        }
        val fonds = fondsDesStyles(zip)
        val result = linkedMapOf<String, Int>()
        for ((l, cellules) in params.lignes) {
            if (l <= lh) continue
            val nom = cellules[col]?.trim().orEmpty()
            if (nom.isEmpty()) continue
            val couleur = styleParRef[Ref.de(col, l)]?.let { fonds.getOrNull(it) } ?: continue
            result[nom] = couleur
        }
        return result
    }

    /** Pour chaque style de cellule (cellXfs) : couleur de fond ARGB, ou null. */
    private fun fondsDesStyles(zip: ZipFile): List<Int?> {
        val doc = zip.getEntry("xl/styles.xml")?.let { e -> zip.getInputStream(e).use { ExcelExporter.parse(it.readBytes()) } } ?: return emptyList()
        val theme = couleursTheme(zip)
        val fillsEl = doc.getElementsByTagName("fills").item(0) as? Element ?: return emptyList()
        val fills = ExcelExporter.enfants(fillsEl, "fill").map { f ->
            val pattern = f.getElementsByTagName("patternFill").item(0) as? Element
            val fg = pattern?.getElementsByTagName("fgColor")?.item(0) as? Element
            if (pattern == null || pattern.getAttribute("patternType") == "none" || fg == null) null else couleur(fg, theme)
        }
        val xfsEl = doc.getElementsByTagName("cellXfs").item(0) as? Element ?: return emptyList()
        return ExcelExporter.enfants(xfsEl, "xf").map { xf -> xf.getAttribute("fillId").toIntOrNull()?.let { fills.getOrNull(it) } }
    }

    private fun couleur(e: Element, theme: List<Int>): Int? {
        val base = when {
            e.hasAttribute("rgb") -> e.getAttribute("rgb").takeLast(6).toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
            e.hasAttribute("theme") -> e.getAttribute("theme").toIntOrNull()?.let { theme.getOrNull(it) }
            else -> null
        } ?: return null
        val tint = e.getAttribute("tint").toDoubleOrNull() ?: 0.0
        return if (tint == 0.0) base else appliquerTeinte(base, tint)
    }

    /** Couleurs du thème dans l'ordre des index Excel (0 lt1, 1 dk1, 2 lt2, 3 dk2, 4-9 accents…). */
    private fun couleursTheme(zip: ZipFile): List<Int> {
        val entree = zip.entries().asSequence().firstOrNull { it.name.startsWith("xl/theme/") && it.name.endsWith(".xml") } ?: return emptyList()
        val doc = zip.getInputStream(entree).use { ExcelExporter.parse(it.readBytes()) }
        val schema = doc.getElementsByTagName("a:clrScheme").item(0) as? Element ?: return emptyList()
        val parNom = mutableMapOf<String, Int>()
        var n = schema.firstChild
        while (n != null) {
            val e = n as? Element
            if (e != null) {
                val c = (e.getElementsByTagName("a:srgbClr").item(0) as? Element)?.getAttribute("val")
                    ?: (e.getElementsByTagName("a:sysClr").item(0) as? Element)?.getAttribute("lastClr")
                c?.toIntOrNull(16)?.let { parNom[e.tagName.removePrefix("a:")] = it or 0xFF000000.toInt() }
            }
            n = n.nextSibling
        }
        return listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
            .map { parNom[it] ?: GRIS }
    }

    /** Teinte Excel : éclaircit (tint > 0) ou assombrit (tint < 0) la luminance HSL. */
    private fun appliquerTeinte(argb: Int, tint: Double): Int {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        var l = (max + min) / 2
        val d = max - min
        val s = if (d == 0.0) 0.0 else d / (1 - Math.abs(2 * l - 1))
        var h = when {
            d == 0.0 -> 0.0
            max == r -> ((g - b) / d) % 6
            max == g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60
        if (h < 0) h += 360
        l = if (tint < 0) l * (1 + tint) else l * (1 - tint) + tint
        val c = (1 - Math.abs(2 * l - 1)) * s
        val x = c * (1 - Math.abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun o(v: Double) = Math.round((v + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (o(r1) shl 16) or (o(g1) shl 8) or o(b1)
    }
}
