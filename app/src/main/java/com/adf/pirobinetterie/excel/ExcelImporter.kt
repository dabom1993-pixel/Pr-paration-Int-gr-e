package com.adf.pirobinetterie.excel

import com.adf.pirobinetterie.model.Abaque
import com.adf.pirobinetterie.model.Bride
import com.adf.pirobinetterie.model.ChampsBride
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.EnTete
import com.adf.pirobinetterie.model.Etat
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.LigneAbaque
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.cle
import java.io.File

/**
 * Construit un [Projet] à partir du classeur de préparation (onglets Instruction, Matos,
 * Suivi et DATA). Les colonnes sont retrouvées par leur libellé, jamais par leur lettre.
 */
object ExcelImporter {

    class ImportException(message: String) : Exception(message)

    const val ONGLET_MATOS = "Matos"
    const val ONGLET_SUIVI = "Suivi"
    const val ONGLET_DATA = "DATA"
    const val ONGLET_INSTRUCTION = "Instruction"

    /** En-têtes d'un onglet : numéro de ligne + colonne -> clé, et clé -> libellé d'origine. */
    class EnTetes(val ligne: Int, val colonnes: LinkedHashMap<Int, String>, val libelles: LinkedHashMap<String, String>)

    /**
     * [images] : nom d'item -> (chemin photo, chemin plot plan) déjà copiés dans l'app ("" si absent).
     */
    fun importer(fichier: File, nomFichier: String, images: (String) -> Pair<String, String>): Projet {
        Classeur(fichier).use { classeur ->
            val matos = classeur.lireOnglet(ONGLET_MATOS)
                ?: throw ImportException("Onglet \"$ONGLET_MATOS\" introuvable dans le fichier.")
            val suivi = classeur.lireOnglet(ONGLET_SUIVI)
                ?: throw ImportException("Onglet \"$ONGLET_SUIVI\" introuvable dans le fichier.")
            val data = classeur.lireOnglet(ONGLET_DATA)
            val instruction = classeur.lireOnglet(ONGLET_INSTRUCTION)

            val enTetesMatos = enTetesMatos(matos)
                ?: throw ImportException("Ligne d'en-têtes introuvable dans \"$ONGLET_MATOS\" (colonnes Unité / Item / Rep. attendues).")
            val enTetesSuivi = enTetesSuivi(suivi)
                ?: throw ImportException("Ligne d'en-têtes introuvable dans \"$ONGLET_SUIVI\" (colonnes Item / Travaux attendues).")

            val brides = lireLignes(matos, enTetesMatos)
                .filter { !it[ChampsBride.ITEM].isNullOrBlank() }
                .map { Bride(v = it) }

            val bridesParItem = brides.groupBy { cleItem(it[ChampsBride.ITEM]) }
            val nomsSuivi = mutableSetOf<String>()
            val items = mutableListOf<Item>()
            for (valeurs in lireLignes(suivi, enTetesSuivi)) {
                val nom = valeurs[ChampsFiche.NOM]?.trim().orEmpty()
                if (nom.isEmpty() || !nomsSuivi.add(cleItem(nom))) continue
                val bridesItem = bridesParItem[cleItem(nom)].orEmpty()
                val (photo, plan) = images(nom)
                val initial = Etat(LinkedHashMap(valeurs), bridesItem.map { it.copie() }, photo, plan)
                items.add(
                    Item(
                        nom = nom, initial = initial, v = LinkedHashMap(valeurs),
                        brides = bridesItem.map { it.copie() }.toMutableList(), photo = photo, plan = plan
                    )
                )
            }
            val orphelines = brides.filter { cleItem(it[ChampsBride.ITEM]) !in nomsSuivi }

            val enTete = lireEnTete(instruction, matos)
            val (listes, abaque) = if (data != null) lireData(data) else (emptyMap<String, List<String>>() to Abaque.VIDE)

            return Projet(
                fichierSource = nomFichier,
                enTete = enTete,
                enTeteInitial = enTete.copie(),
                items = items,
                bridesOrphelines = orphelines,
                listes = listes,
                abaque = abaque,
                colonnesSuivi = enTetesSuivi.libelles,
                colonnesMatos = enTetesMatos.libelles
            )
        }
    }

    fun cleItem(nom: String): String = nom.trim().lowercase()

    // --- En-têtes -------------------------------------------------------------------------

    /**
     * [alias] ramène les libellés des différentes versions du fichier à une même clé. Une colonne
     * sans vrai titre ("Colonne3") prend le titre de groupe écrit juste au-dessus ("RAAT").
     */
    private fun enTetes(feuille: Feuille, ligne: Int, alias: (String) -> String): EnTetes {
        val colonnes = LinkedHashMap<Int, String>()
        val libelles = LinkedHashMap<String, String>()
        for ((col, brut) in feuille.ligne(ligne)) {
            var texte = brut
            if (Regex("colonne\\d*").matches(cle(texte))) {
                texte = feuille.valeur(ligne - 1, col).ifBlank { texte }
            }
            val base = alias(cle(texte))
            if (base.isEmpty()) continue
            var k = base
            // Deuxième "Matière" d'une ligne Matos = matière des tiges.
            if (k in libelles && k == ChampsBride.MATIERE_JOINT) k = ChampsBride.MATIERE_TIGE
            // Autre libellé en double : on garde les deux colonnes, la seconde suffixée.
            var n = 2
            while (k in libelles) k = base + "_" + n++
            colonnes[col] = k
            libelles[k] = texte.replace('\n', ' ').trim()
        }
        return EnTetes(ligne, colonnes, libelles)
    }

    fun enTetesMatos(feuille: Feuille): EnTetes? {
        for (ligne in feuille.lignes.keys.filter { it <= 30 }) {
            val cles = feuille.ligne(ligne).values.map { ChampsBride.alias(cle(it)) }.toSet()
            if (ChampsBride.UNITE in cles && ChampsBride.ITEM in cles && ChampsBride.REP in cles) {
                return enTetes(feuille, ligne, ChampsBride::alias)
            }
        }
        return null
    }

    /**
     * Suivi a deux lignes d'en-têtes (abrégés en ligne 3, libellés complets en ligne 4) : on
     * retient la dernière ligne contenant "Item" (ou "Nom") et "Travaux".
     */
    fun enTetesSuivi(feuille: Feuille): EnTetes? {
        val candidates = feuille.lignes.keys.filter { it <= 30 }.filter { ligne ->
            val cles = feuille.ligne(ligne).values.map { ChampsFiche.alias(cle(it)) }.toSet()
            ChampsFiche.NOM in cles && "travaux" in cles
        }
        return candidates.maxOrNull()?.let { enTetes(feuille, it, ChampsFiche::alias) }
    }

    /** Lignes de données situées sous la ligne d'en-têtes (clé de colonne -> valeur). */
    fun lireLignes(feuille: Feuille, enTetes: EnTetes): List<LinkedHashMap<String, String>> {
        val result = mutableListOf<LinkedHashMap<String, String>>()
        for ((ligne, cellules) in feuille.lignes) {
            if (ligne <= enTetes.ligne) continue
            val valeurs = LinkedHashMap<String, String>()
            for ((col, k) in enTetes.colonnes) {
                var v = cellules[col]?.trim().orEmpty()
                // Colonne de date sans format date dans l'Excel : numéro de série -> jj/mm/aaaa.
                if (k.startsWith("date")) v.toDoubleOrNull()?.takeIf { it in 20000.0..80000.0 }?.let { v = Classeur.dateExcel(it) }
                if (v.isNotEmpty()) valeurs[k] = v
            }
            if (valeurs.isNotEmpty()) result.add(valeurs)
        }
        return result
    }

    // --- Client / Lieu / Unité / Année ---------------------------------------------------

    private fun lireEnTete(instruction: Feuille?, matos: Feuille): EnTete {
        val valeurs = mutableMapOf<String, String>()
        val cles = setOf("client", "lieu", "unite", "annee")
        // Onglet Instruction : libellé en colonne A, valeur en colonne B (ignorée si c'est
        // encore le texte d'exemple "Client", "Lieu"...).
        instruction?.lignes?.forEach { (_, cellules) ->
            val k = cle(cellules[1].orEmpty())
            val v = cellules[2].orEmpty().trim()
            if (k in cles && v.isNotEmpty() && cle(v) != k && k !in valeurs) valeurs[k] = v
        }
        // Repli : en-tête de l'onglet Matos ("Client" ... "TOTALENERGIES").
        matos.lignes.filterKeys { it <= 5 }.forEach { (_, cellules) ->
            val cols = cellules.keys.toList()
            for ((i, col) in cols.withIndex()) {
                val k = cle(cellules.getValue(col))
                if (k in cles && k !in valeurs) {
                    cols.drop(i + 1).firstOrNull()?.let { valeurs[k] = cellules.getValue(it).trim() }
                }
            }
        }
        return EnTete(valeurs["client"].orEmpty(), valeurs["lieu"].orEmpty(), valeurs["unite"].orEmpty(), valeurs["annee"].orEmpty())
    }

    // --- Onglet DATA : listes déroulantes + abaque ---------------------------------------

    private fun lireData(data: Feuille): Pair<Map<String, List<String>>, Abaque> {
        val ligneEnTetes = data.lignes.keys.filter { it <= 30 }.firstOrNull { ligne ->
            val cles = data.ligne(ligne).values.map { cle(it) }.toSet()
            "type" in cles && "dn" in cles
        } ?: return emptyMap<String, List<String>>() to Abaque.VIDE

        // Listes : colonnes d'en-têtes de gauche à droite, jusqu'à retrouver "DN" une seconde
        // fois (début des tableaux de boulonnerie). Un autre libellé en double est suffixé.
        val listes = linkedMapOf<String, List<String>>()
        val colonnesListes = mutableListOf<Pair<Int, String>>()
        for ((col, texte) in data.ligne(ligneEnTetes)) {
            val base = (if ('Ø' in texte || 'ø' in texte) "diam" else "") + cle(texte)
            if (base.isEmpty()) continue
            if (base == "dn" && "dn" in listes) break
            var k = base
            var n = 2
            while (k in listes) k = base + "_" + n++
            colonnesListes.add(col to k)
            listes[k] = emptyList()
        }
        for ((col, k) in colonnesListes) {
            listes[k] = data.lignes.filterKeys { it > ligneEnTetes }.values
                .mapNotNull { it[col]?.trim()?.takeIf { v -> v.isNotEmpty() } }
                .distinct()
        }
        return listes to lireAbaque(data, ligneEnTetes)
    }

    /**
     * Bloc "ABBAQUE" : colonnes DN, Série, Nb Boul, Ø tige2 (M 14...), Lg tige RF, Lg tige RTJ.
     * On part de la cellule titre "ABBAQUE" (sinon du premier bloc "Nb Boul" de la feuille).
     */
    private fun lireAbaque(data: Feuille, ligneEnTetes: Int): Abaque {
        val depart = data.lignes.values.flatMap { it.entries }
            .firstOrNull { cle(it.value) in setOf("abbaque", "abaque") }?.key ?: 1
        val enTetes = data.ligne(ligneEnTetes).mapValues { (_, t) -> (if ('Ø' in t || 'ø' in t) "diam" else "") + cle(t) }
        val nb = enTetes.entries.firstOrNull { it.key >= depart && it.value == "nbboul" }?.key ?: return Abaque.VIDE
        fun avant(k: String) = enTetes.entries.filter { it.key < nb && it.value == k }.maxByOrNull { it.key }?.key
        fun apres(vararg ks: String) = ks.firstNotNullOfOrNull { k ->
            enTetes.entries.filter { it.key > nb && it.key <= nb + 6 && it.value == k }.minByOrNull { it.key }?.key
        }
        val colDn = avant("dn") ?: return Abaque.VIDE
        val colSerie = avant("serie") ?: return Abaque.VIDE
        val colDiam = apres("diamtige2", "diamtige")
        val colRf = apres("lgtigerf")
        val colRtj = apres("lgtigertj")

        val lignes = data.lignes.filterKeys { it > ligneEnTetes }.values.mapNotNull { c ->
            val dn = c[colDn]?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            val serie = c[colSerie]?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            val nbTiges = c[nb].orEmpty()
            if (nbTiges.isBlank()) return@mapNotNull null
            LigneAbaque(
                dn, serie, nbTiges,
                colDiam?.let { c[it] }.orEmpty(),
                colRf?.let { c[it] }.orEmpty(),
                colRtj?.let { c[it] }.orEmpty()
            )
        }
        return Abaque(lignes)
    }
}
