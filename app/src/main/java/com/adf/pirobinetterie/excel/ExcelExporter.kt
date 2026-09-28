package com.adf.pirobinetterie.excel

import com.adf.pirobinetterie.model.Bride
import com.adf.pirobinetterie.model.ChampsBride
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.Diff
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.cle
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Produit une copie mise à jour du classeur importé (le fichier d'origine n'est jamais modifié) :
 * - Suivi : une ligne par item, seules les cellules modifiées sont réécrites, colonne "Rév" =
 *   numéro de la dernière révision validée (pas de ligne dupliquée par révision) ;
 * - Matos : une ligne par bride, brides ajoutées / supprimées sur la tablette répercutées ;
 * - Instruction : Client / Lieu / Unité / Année si modifiés ;
 * - fond jaune sur les différences entre la dernière révision (n) et la précédente (n-1).
 * Les autres onglets, les macros et la mise en forme sont conservés tels quels.
 */
object ExcelExporter {

    class ExportException(message: String) : Exception(message)

    fun exporter(source: File, projet: Projet, destination: File) {
        val entrees = LinkedHashMap<String, ByteArray>()
        ZipFile(source).use { zip ->
            for (e in zip.entries()) if (!e.isDirectory) entrees[e.name] = zip.getInputStream(e).use { it.readBytes() }
        }

        val onglets: Map<String, String>
        val feuilleSuivi: Feuille
        val feuilleMatos: Feuille
        val feuilleInstruction: Feuille?
        Classeur(source).use { c ->
            onglets = c.onglets
            feuilleSuivi = c.lireOnglet(ExcelImporter.ONGLET_SUIVI) ?: throw ExportException("Onglet Suivi introuvable dans le fichier source.")
            feuilleMatos = c.lireOnglet(ExcelImporter.ONGLET_MATOS) ?: throw ExportException("Onglet Matos introuvable dans le fichier source.")
            feuilleInstruction = c.lireOnglet(ExcelImporter.ONGLET_INSTRUCTION)
        }
        fun chemin(nom: String) = onglets.entries.firstOrNull { it.key.trim().equals(nom, true) }?.value

        val styles = entrees["xl/styles.xml"]?.let { Styles(parse(it)) }

        chemin(ExcelImporter.ONGLET_SUIVI)?.let { p ->
            val doc = parse(entrees.getValue(p))
            majSuivi(doc, feuilleSuivi, projet, styles)
            entrees[p] = serialiser(doc)
        }
        chemin(ExcelImporter.ONGLET_MATOS)?.let { p ->
            val doc = parse(entrees.getValue(p))
            val derniereLigne = majMatos(doc, feuilleMatos, projet, styles)
            entrees[p] = serialiser(doc)
            if (derniereLigne != null) etendreTableau(entrees, p, derniereLigne)
        }
        chemin(ExcelImporter.ONGLET_INSTRUCTION)?.let { p ->
            if (feuilleInstruction == null) return@let
            val doc = parse(entrees.getValue(p))
            if (majInstruction(doc, feuilleInstruction, projet)) entrees[p] = serialiser(doc)
        }
        styles?.let { if (it.modifie) entrees["xl/styles.xml"] = serialiser(it.doc) }
        forcerRecalcul(entrees)

        FileOutputStream(destination).use { fos ->
            ZipOutputStream(fos.buffered()).use { zout ->
                for ((nom, contenu) in entrees) {
                    zout.putNextEntry(ZipEntry(nom))
                    zout.write(contenu)
                    zout.closeEntry()
                }
            }
        }
    }

    // --- Suivi ----------------------------------------------------------------------------

    private fun majSuivi(doc: Document, feuille: Feuille, projet: Projet, styles: Styles?) {
        val enTetes = ExcelImporter.enTetesSuivi(feuille) ?: throw ExportException("En-têtes de l'onglet Suivi introuvables.")
        val feuilleXml = FeuilleXml(doc)
        val colonnes = LinkedHashMap(enTetes.colonnes) // col -> clé

        // Colonnes de la Fiche absentes de Suivi (N° Ligne...) : ajoutées si renseignées.
        val itemsParNom = projet.items.associateBy { ExcelImporter.cleItem(it.nom) }
        var derniereCol = colonnes.keys.maxOrNull() ?: 0
        val ligneEnTete = feuilleXml.ligne(enTetes.ligne)
        val styleEnTete = colonnes.keys.maxOrNull()?.let { feuilleXml.style(ligneEnTete, it) }
        for ((k, libelle) in ChampsFiche.colonnesAjoutees) {
            if (k in colonnes.values) continue
            if (projet.items.none { it.etatExporte().v[k].orEmpty().isNotBlank() }) continue
            derniereCol++
            colonnes[derniereCol] = k
            feuilleXml.ecrire(ligneEnTete, derniereCol, libelle, styleEnTete)
        }
        val colRev = colonnes.entries.firstOrNull { it.value == ChampsFiche.REV }?.key
        val colNom = colonnes.entries.firstOrNull { it.value == ChampsFiche.NOM }?.key ?: return

        for ((numLigne, cellules) in feuille.lignes) {
            if (numLigne <= enTetes.ligne) continue
            val item = itemsParNom[ExcelImporter.cleItem(cellules[colNom].orEmpty())] ?: continue
            val exporte = item.etatExporte()
            val diff = diffDerniere(item)
            val ligne = feuilleXml.ligne(numLigne)
            for ((col, k) in colonnes) {
                if (col == colRev) continue
                val avant = item.initial.v[k].orEmpty().trim()
                val apres = exporte.v[k].orEmpty().trim()
                val jaune = diff.champ(k)
                if (avant != apres) {
                    feuilleXml.ecrire(ligne, col, apres, jauneSi(styles, feuilleXml.style(ligne, col), jaune))
                } else if (jaune) {
                    feuilleXml.styler(ligne, col, jauneSi(styles, feuilleXml.style(ligne, col), true))
                }
            }
            val rev = item.derniere
            if (colRev != null && rev != null) feuilleXml.ecrire(ligne, colRev, rev.num.toString(), null)
        }
    }

    // --- Matos ----------------------------------------------------------------------------

    /** Réécrit les lignes de brides ; retourne la dernière ligne écrite si le tableau doit s'étendre. */
    private fun majMatos(doc: Document, feuille: Feuille, projet: Projet, styles: Styles?): Int? {
        val enTetes = ExcelImporter.enTetesMatos(feuille) ?: throw ExportException("En-têtes de l'onglet Matos introuvables.")
        val feuilleXml = FeuilleXml(doc)
        val colItem = enTetes.colonnes.entries.first { it.value == ChampsBride.ITEM }.key

        // Ordre d'origine : chaque item garde la place de sa première bride dans Matos.
        val ordre = LinkedHashSet<String>()
        for ((numLigne, c) in feuille.lignes) {
            if (numLigne > enTetes.ligne) c[colItem]?.trim()?.takeIf { it.isNotEmpty() }?.let { ordre.add(ExcelImporter.cleItem(it)) }
        }
        projet.items.forEach { ordre.add(ExcelImporter.cleItem(it.nom)) }
        val itemsParNom = projet.items.associateBy { ExcelImporter.cleItem(it.nom) }
        val orphelines = projet.bridesOrphelines.groupBy { ExcelImporter.cleItem(it[ChampsBride.ITEM]) }

        val sortie = mutableListOf<Pair<Bride, Diff>>()
        for (cleItem in ordre) {
            val item = itemsParNom[cleItem]
            if (item != null) {
                val diff = diffDerniere(item)
                item.etatExporte().brides.forEach { b ->
                    // Une bride appartient toujours à son item, même si la case Item est vide.
                    val copie = b.copie()
                    if (copie[ChampsBride.ITEM].isBlank()) copie[ChampsBride.ITEM] = item.nom
                    sortie.add(copie to diff)
                }
            } else {
                orphelines[cleItem]?.forEach { sortie.add(it to Diff.VIDE) }
            }
        }

        val premiere = enTetes.ligne + 1
        val stylesModele = feuilleXml.ligneExistante(premiere)?.let { l -> enTetes.colonnes.keys.associateWith { feuilleXml.style(l, it) } }.orEmpty()
        val ancienneDerniere = feuilleXml.derniereLigne()

        sortie.forEachIndexed { i, (bride, diff) ->
            val ligne = feuilleXml.ligne(premiere + i)
            for ((col, k) in enTetes.colonnes) {
                val style = feuilleXml.styleSiExiste(ligne, col) ?: stylesModele[col]
                feuilleXml.ecrire(ligne, col, bride[k].trim(), jauneSi(styles, style, diff.cellule(bride.id, k)))
            }
        }
        val fin = premiere + sortie.size - 1
        for (n in (fin + 1)..ancienneDerniere) {
            val ligne = feuilleXml.ligneExistante(n) ?: continue
            for (col in enTetes.colonnes.keys) feuilleXml.effacer(ligne, col)
        }
        feuilleXml.majDimension()
        return if (fin > ancienneDerniere) fin else null
    }

    /** Étend la plage du tableau structuré Excel de l'onglet si des lignes ont été ajoutées au-delà. */
    private fun etendreTableau(entrees: MutableMap<String, ByteArray>, cheminFeuille: String, derniereLigne: Int) {
        val rels = cheminFeuille.substringBeforeLast('/') + "/_rels/" + cheminFeuille.substringAfterLast('/') + ".rels"
        val relsDoc = entrees[rels]?.let { parse(it) } ?: return
        val relations = relsDoc.getElementsByTagName("Relationship")
        for (i in 0 until relations.length) {
            val r = relations.item(i) as Element
            if (!r.getAttribute("Type").endsWith("/table")) continue
            val cible = r.getAttribute("Target")
            val cheminTable = if (cible.startsWith("/")) cible.removePrefix("/")
            else normaliser(cheminFeuille.substringBeforeLast('/') + "/" + cible)
            val tableDoc = entrees[cheminTable]?.let { parse(it) } ?: continue
            val table = tableDoc.documentElement
            val nouvelle = etendreRef(table.getAttribute("ref"), derniereLigne) ?: continue
            table.setAttribute("ref", nouvelle)
            val filtres = tableDoc.getElementsByTagName("autoFilter")
            for (j in 0 until filtres.length) (filtres.item(j) as Element).setAttribute("ref", nouvelle)
            entrees[cheminTable] = serialiser(tableDoc)
        }
    }

    private fun etendreRef(ref: String, derniereLigne: Int): String? {
        val parties = ref.split(":")
        if (parties.size != 2) return null
        val (col, ligne) = Ref.decoupe(parties[1])
        if (derniereLigne <= ligne) return null
        return parties[0] + ":" + Ref.de(col, derniereLigne)
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

    // --- Instruction ----------------------------------------------------------------------

    private fun majInstruction(doc: Document, feuille: Feuille, projet: Projet): Boolean {
        val valeurs = mapOf(
            "client" to (projet.enTeteInitial.client to projet.enTete.client),
            "lieu" to (projet.enTeteInitial.lieu to projet.enTete.lieu),
            "unite" to (projet.enTeteInitial.unite to projet.enTete.unite),
            "annee" to (projet.enTeteInitial.annee to projet.enTete.annee)
        ).filter { (_, v) -> v.first.trim() != v.second.trim() }
        if (valeurs.isEmpty()) return false
        val feuilleXml = FeuilleXml(doc)
        var modifie = false
        for ((numLigne, cellules) in feuille.lignes) {
            val k = cle(cellules[1].orEmpty())
            val v = valeurs[k] ?: continue
            val ligne = feuilleXml.ligne(numLigne)
            feuilleXml.ecrire(ligne, 2, v.second.trim(), null)
            modifie = true
        }
        return modifie
    }

    // --- Divers ---------------------------------------------------------------------------

    /** Différences à surligner : dernière révision validée comparée à la précédente. */
    private fun diffDerniere(item: Item): Diff {
        val n = item.derniere?.num ?: return Diff.VIDE
        return if (n >= 1) item.diffRevision(n) else Diff.VIDE
    }

    private fun jauneSi(styles: Styles?, style: Int?, jaune: Boolean): Int? =
        if (jaune && styles != null) styles.jaune(style ?: 0) else style

    /**
     * La chaîne de calcul (calcChain.xml) référence des cellules à formule : on la supprime (Excel
     * la reconstruit) et on demande un recalcul complet à l'ouverture.
     */
    private fun forcerRecalcul(entrees: MutableMap<String, ByteArray>) {
        if (entrees.remove("xl/calcChain.xml") != null) {
            entrees["[Content_Types].xml"]?.let { b ->
                val doc = parse(b)
                val overrides = doc.getElementsByTagName("Override")
                for (i in overrides.length - 1 downTo 0) {
                    val o = overrides.item(i) as Element
                    if (o.getAttribute("PartName") == "/xl/calcChain.xml") o.parentNode.removeChild(o)
                }
                entrees["[Content_Types].xml"] = serialiser(doc)
            }
            entrees["xl/_rels/workbook.xml.rels"]?.let { b ->
                val doc = parse(b)
                val rels = doc.getElementsByTagName("Relationship")
                for (i in rels.length - 1 downTo 0) {
                    val r = rels.item(i) as Element
                    if (r.getAttribute("Target").endsWith("calcChain.xml")) r.parentNode.removeChild(r)
                }
                entrees["xl/_rels/workbook.xml.rels"] = serialiser(doc)
            }
        }
        entrees["xl/workbook.xml"]?.let { b ->
            val doc = parse(b)
            val calc = doc.getElementsByTagName("calcPr")
            if (calc.length > 0) {
                (calc.item(0) as Element).setAttribute("fullCalcOnLoad", "1")
                entrees["xl/workbook.xml"] = serialiser(doc)
            }
        }
    }

    internal fun parse(bytes: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        return bytes.inputStream().use { f.newDocumentBuilder().parse(it) }
    }

    internal fun serialiser(doc: Document): ByteArray {
        doc.xmlStandalone = true
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.INDENT, "no")
        val out = ByteArrayOutputStream()
        t.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    /** Ajoute au besoin une copie "fond jaune" de chaque style de cellule utilisé. */
    private class Styles(val doc: Document) {
        var modifie = false
            private set
        private val copies = mutableMapOf<Int, Int>()
        private var fillJaune: Int? = null

        fun jaune(style: Int): Int = copies.getOrPut(style) {
            val cellXfs = doc.getElementsByTagName("cellXfs").item(0) as? Element ?: return style
            val xfs = enfants(cellXfs, "xf")
            val modele = xfs.getOrNull(style) ?: xfs.firstOrNull() ?: return style
            val copie = modele.cloneNode(true) as Element
            copie.setAttribute("fillId", fill().toString())
            copie.setAttribute("applyFill", "1")
            cellXfs.appendChild(copie)
            cellXfs.setAttribute("count", (xfs.size + 1).toString())
            modifie = true
            xfs.size
        }

        private fun fill(): Int = fillJaune ?: run {
            val fills = doc.getElementsByTagName("fills").item(0) as Element
            val nb = enfants(fills, "fill").size
            val fill = doc.createElement("fill")
            val pattern = doc.createElement("patternFill").apply { setAttribute("patternType", "solid") }
            pattern.appendChild(doc.createElement("fgColor").apply { setAttribute("rgb", "FFFFFF00") })
            pattern.appendChild(doc.createElement("bgColor").apply { setAttribute("indexed", "64") })
            fill.appendChild(pattern)
            fills.appendChild(fill)
            fills.setAttribute("count", (nb + 1).toString())
            fillJaune = nb
            nb
        }
    }

    internal fun enfants(parent: Element, nom: String): List<Element> {
        val result = mutableListOf<Element>()
        var n: Node? = parent.firstChild
        while (n != null) {
            if (n is Element && Classeur.local(n.tagName) == nom) result.add(n)
            n = n.nextSibling
        }
        return result
    }
}

/** Manipulation des lignes / cellules d'un XML de feuille (sheetData). */
internal class FeuilleXml(private val doc: Document) {

    private val sheetData: Element = doc.getElementsByTagName("sheetData").item(0) as? Element
        ?: throw ExcelExporter.ExportException("Structure de feuille inattendue (sheetData introuvable).")
    private val lignes = sortedMapOf<Int, Element>().apply {
        for (l in ExcelExporter.enfants(sheetData, "row")) l.getAttribute("r").toIntOrNull()?.let { put(it, l) }
    }

    fun derniereLigne(): Int = lignes.keys.maxOrNull() ?: 0

    fun ligneExistante(num: Int): Element? = lignes[num]

    fun ligne(num: Int): Element = lignes[num] ?: run {
        val l = doc.createElement("row")
        l.setAttribute("r", num.toString())
        val suivante = lignes.tailMap(num + 1).values.firstOrNull()
        if (suivante != null) sheetData.insertBefore(l, suivante) else sheetData.appendChild(l)
        lignes[num] = l
        l
    }

    private fun cellules(ligne: Element): LinkedHashMap<Int, Element> {
        val result = LinkedHashMap<Int, Element>()
        for (c in ExcelExporter.enfants(ligne, "c")) {
            val ref = c.getAttribute("r")
            if (ref.isNotEmpty()) result[Ref.decoupe(ref).first] = c
        }
        return result
    }

    private fun cellule(ligne: Element, col: Int, creer: Boolean): Element? {
        cellules(ligne)[col]?.let { return it }
        if (!creer) return null
        val c = doc.createElement("c")
        c.setAttribute("r", Ref.de(col, ligne.getAttribute("r").toInt()))
        val suivante = cellules(ligne).entries.firstOrNull { it.key > col }?.value
        if (suivante != null) ligne.insertBefore(c, suivante) else ligne.appendChild(c)
        return c
    }

    /** Style (index cellXfs) effectif d'une cellule : le sien, sinon celui de la ligne. */
    fun style(ligne: Element, col: Int): Int? = styleSiExiste(ligne, col)
        ?: ligne.getAttribute("s").takeIf { ligne.getAttribute("customFormat") == "1" }?.toIntOrNull()

    fun styleSiExiste(ligne: Element, col: Int): Int? =
        cellule(ligne, col, false)?.getAttribute("s")?.toIntOrNull()

    fun styler(ligne: Element, col: Int, style: Int?) {
        if (style == null) return
        cellule(ligne, col, true)!!.setAttribute("s", style.toString())
    }

    /** Écrit une valeur (nombre si c'en est un, texte sinon) ; "" vide la cellule. */
    fun ecrire(ligne: Element, col: Int, valeur: String, style: Int?) {
        if (valeur.isEmpty()) {
            val c = cellule(ligne, col, style != null) ?: return
            viderContenu(c)
            if (style != null) c.setAttribute("s", style.toString())
            return
        }
        val c = cellule(ligne, col, true)!!
        viderContenu(c)
        if (style != null) c.setAttribute("s", style.toString())
        if (NOMBRE.matches(valeur)) {
            c.appendChild(doc.createElement("v").apply { textContent = valeur.replace(',', '.') })
        } else {
            c.setAttribute("t", "inlineStr")
            val t = doc.createElement("t")
            if (valeur != valeur.trim() || '\n' in valeur) t.setAttribute("xml:space", "preserve")
            t.textContent = valeur
            c.appendChild(doc.createElement("is").apply { appendChild(t) })
        }
    }

    fun effacer(ligne: Element, col: Int) {
        cellule(ligne, col, false)?.let { viderContenu(it) }
    }

    private fun viderContenu(c: Element) {
        while (c.hasChildNodes()) c.removeChild(c.firstChild)
        c.removeAttribute("t")
    }

    fun majDimension() {
        val dims = doc.getElementsByTagName("dimension")
        if (dims.length == 0) return
        val d = dims.item(0) as Element
        val ref = d.getAttribute("ref")
        val parties = ref.split(":")
        if (parties.size != 2) return
        val (col, ligne) = Ref.decoupe(parties[1])
        val derniere = derniereLigne()
        if (derniere > ligne) d.setAttribute("ref", parties[0] + ":" + Ref.de(col, derniere))
    }

    companion object {
        /** Nombre "propre" (pas de zéro de tête qui serait perdu : "01" reste du texte). */
        private val NOMBRE = Regex("^-?(0|[1-9]\\d{0,14})([.,]\\d+)?$")
    }
}
