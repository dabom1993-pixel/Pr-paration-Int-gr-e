package com.adf.pirobinetterie.pdf

import com.adf.pirobinetterie.model.Champ
import com.adf.pirobinetterie.model.ChampsBride
import com.adf.pirobinetterie.model.ChampsFiche
import com.adf.pirobinetterie.model.Diff
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.Projet

/** Surface de dessin (PDF Android sur la tablette, image PNG pour les essais). Unités : points. */
interface Toile {
    fun rect(x: Float, y: Float, w: Float, h: Float, fond: Int?, bordure: Int?, epaisseur: Float = 0.5f)
    fun largeurTexte(texte: String, taille: Float, gras: Boolean): Float
    fun texte(texte: String, x: Float, yBase: Float, taille: Float, gras: Boolean, couleur: Int)
    /** Dessine l'image [chemin] centrée dans le cadre, sans déformation. false si illisible. */
    fun image(chemin: String, x: Float, y: Float, w: Float, h: Float): Boolean
}

class LigneRevision(val num: Int, val date: String, val objet: String)

class LigneBride(val repere: String, val designation: String, val valeurs: List<String>, val jaunes: List<Boolean>, val reperJaune: Boolean)

/** Tout ce qui figure sur la fiche d'un item pour une révision donnée. */
class DonneesFiche(
    val titre: String,
    val item: String,
    val logoAdf: String?,
    val logoClient: String?,
    val revision: Int,
    val dateRevision: String,
    val revisions: List<LigneRevision>,
    val valeurs: Map<String, String>,
    val jaunes: Set<String>,
    val photo: String,
    val plan: String,
    val photoJaune: Boolean,
    val planJaune: Boolean,
    val brides: List<LigneBride>
) {
    companion object {
        /** Données de la fiche de la révision [num] de [item] (jaune = différences avec n-1). */
        fun pour(projet: Projet, item: Item, num: Int, logoAdf: String?, logoClient: String?): DonneesFiche {
            val rev = item.revisions.first { it.num == num }
            val etat = rev.etat
            val diff: Diff = item.diffRevision(num)
            val colonnes = ChampsBride.colonnesFiche
            val brides = etat.brides.map { b ->
                LigneBride(
                    repere = b[ChampsBride.REP],
                    designation = b[ChampsBride.DESIGNATION],
                    valeurs = colonnes.map { b[it.cle] },
                    jaunes = colonnes.map { diff.cellule(b.id, it.cle) },
                    reperJaune = diff.cellule(b.id, ChampsBride.REP) || diff.cellule(b.id, ChampsBride.DESIGNATION)
                )
            }
            return DonneesFiche(
                titre = projet.enTete.titre(),
                item = item.nom,
                logoAdf = logoAdf,
                logoClient = logoClient,
                revision = num,
                dateRevision = rev.date,
                revisions = item.revisions.filter { it.num <= num }.sortedBy { it.num }.takeLast(4)
                    .map { LigneRevision(it.num, it.date, it.objet) },
                valeurs = etat.v,
                jaunes = diff.champs,
                photo = etat.photo,
                plan = etat.plan,
                photoJaune = diff.photo,
                planJaune = diff.plan,
                brides = brides
            )
        }
    }
}

/**
 * Mise en page de l'onglet "Fiche" (A4 paysage) : même grille de colonnes A→Q et de lignes 1→37
 * que l'Excel. 5 brides par page ; au-delà, pages suivantes avec le même en-tête. Numéro de page
 * en bas à droite ("1/1" s'il n'y a qu'une page).
 */
object FicheLayout {

    const val LARGEUR = 842f
    const val HAUTEUR = 595f
    const val BRIDES_PAR_PAGE = 5

    private const val MARGE_X = 22f
    private const val MARGE_HAUT = 12f
    private const val MARGE_BAS = 24f

    private const val NOIR = 0xFF000000.toInt()
    private const val BLANC = 0xFFFFFFFF.toInt()
    private const val GRIS_TEXTE = 0xFF808080.toInt()
    private const val BORDURE = 0xFF7F7F7F.toInt()
    private const val MARINE = 0xFF22385F.toInt()
    private const val LIBELLE = 0xFFEEF1F6.toInt()
    const val JAUNE = 0xFFFFFF00.toInt()

    /** Largeurs des colonnes A..Q de l'onglet Fiche (unités Excel). */
    private val largeursCol = floatArrayOf(4.86f, 10.86f, 9.14f, 9.14f, 8f, 8f, 8f, 8f, 8f, 8f, 8f, 8f, 7f, 9.14f, 9.14f, 9.14f, 9.14f)
    private const val NB_LIGNES = 37
    private fun hauteurLigne(l: Int) = if (l == 32) 30f else 15f

    private val sx = (LARGEUR - 2 * MARGE_X) / largeursCol.sum()
    private val sy = (HAUTEUR - MARGE_HAUT - MARGE_BAS) / (1..NB_LIGNES).sumOf { hauteurLigne(it).toDouble() }.toFloat()

    /** Abscisse du bord gauche de la colonne [c] (1 = A). */
    private fun x(c: Int): Float = MARGE_X + largeursCol.take(c - 1).sum() * sx
    /** Ordonnée du bord haut de la ligne [l] (1 = ligne 1). */
    private fun y(l: Int): Float = MARGE_HAUT + (1 until l).sumOf { hauteurLigne(it).toDouble() }.toFloat() * sy

    /** Rectangle couvrant des cellules fusionnées (colonnes c1 à c2, lignes l1 à l2). */
    private class Zone(val x: Float, val y: Float, val w: Float, val h: Float)
    private fun zone(c1: Int, l1: Int, c2: Int = c1, l2: Int = l1) = Zone(x(c1), y(l1), x(c2 + 1) - x(c1), y(l2 + 1) - y(l1))

    fun nombrePages(d: DonneesFiche): Int = maxOf(1, (d.brides.size + BRIDES_PAR_PAGE - 1) / BRIDES_PAR_PAGE)

    fun dessinerPage(t: Toile, d: DonneesFiche, page: Int) {
        val total = nombrePages(d)
        t.rect(0f, 0f, LARGEUR, HAUTEUR, BLANC, null)
        enTete(t, d)
        val brides = d.brides.drop(page * BRIDES_PAR_PAGE).take(BRIDES_PAR_PAGE)
        if (page == 0) {
            revisions(t, d)
            champs(t, d)
            images(t, d)
            tableauBrides(t, d, brides, 32)
        } else {
            tableauBrides(t, d, brides, 5)
        }
        // Pied de page : révision à gauche, numéro de page à droite.
        val yPied = HAUTEUR - 9f
        t.texte("${d.item} — Rév. ${d.revision} du ${d.dateRevision}", MARGE_X, yPied, 7f, false, GRIS_TEXTE)
        val num = "${page + 1}/$total"
        t.texte(num, LARGEUR - MARGE_X - t.largeurTexte(num, 9f, true), yPied, 9f, true, NOIR)
    }

    private fun enTete(t: Toile, d: DonneesFiche) {
        val logo = zone(1, 1, 2, 3)
        cadre(t, logo)
        d.logoAdf?.let { t.image(it, logo.x + 2, logo.y + 2, logo.w - 4, logo.h - 4) }

        val titre = zone(3, 1, 11, 3)
        cadre(t, titre)
        boite(t, d.titre, titre, 12f, true, Aligne.CENTRE)

        val item = zone(12, 1, 15, 3)
        cadre(t, item)
        boite(t, d.item, item, 16f, true, Aligne.CENTRE)

        val client = zone(16, 1, 17, 3)
        cadre(t, client)
        val logoClient = d.logoClient
        if (logoClient == null || !t.image(logoClient, client.x + 2, client.y + 2, client.w - 4, client.h - 4)) {
            boite(t, "LOGO CLIENT", client, 7f, false, Aligne.CENTRE, GRIS_TEXTE)
        }
    }

    private fun revisions(t: Toile, d: DonneesFiche) {
        cellule(t, zone(1, 5), "Rev", fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)
        cellule(t, zone(2, 5), "Date", fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)
        cellule(t, zone(3, 5, 4, 5), "Objet Rev", fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)
        for (i in 0 until 4) {
            val r = d.revisions.getOrNull(i)
            val l = 6 + i
            cellule(t, zone(1, l), r?.num?.toString().orEmpty(), aligne = Aligne.CENTRE)
            cellule(t, zone(2, l), r?.date.orEmpty(), aligne = Aligne.CENTRE)
            cellule(t, zone(3, l, 4, l), r?.objet.orEmpty())
        }
    }

    private fun champs(t: Toile, d: DonneesFiche) {
        cellule(t, zone(1, 11, 4, 11), "Donnée technique", fond = MARINE, gras = true, couleur = BLANC)
        fun ligne(l: Int, champ: Champ) {
            cellule(t, zone(1, l, 2, l), champ.libelle, fond = LIBELLE, gras = true)
            val jaune = champ.cle in d.jaunes
            cellule(t, zone(3, l, 4, l), d.valeurs[champ.cle].orEmpty(), fond = if (jaune) JAUNE else null)
        }
        ChampsFiche.donneesTechniques.forEachIndexed { i, c -> ligne(12 + i, c) }
        ChampsFiche.besoins.forEachIndexed { i, c -> ligne(20 + i, c) }
        ChampsFiche.travaux.forEachIndexed { i, c -> ligne(25 + i, c) }
    }

    /** Zone E4:Q30 : photo sur les 3/4 haut, localisation sur plot plan sur le 1/4 bas. */
    private fun images(t: Toile, d: DonneesFiche) {
        val z = zone(5, 4, 17, 30)
        val marge = 4f
        val x0 = z.x + marge
        val w = z.w - marge
        val hTotal = z.h - marge
        val hPhoto = hTotal * 0.75f - 2f
        val hPlan = hTotal * 0.25f - 2f
        val yPhoto = z.y + marge / 2
        val yPlan = yPhoto + hPhoto + 4f
        image(t, d.photo, "Photo", Zone(x0, yPhoto, w, hPhoto), d.photoJaune)
        image(t, d.plan, "Localisation sur plot plan", Zone(x0, yPlan, w, hPlan), d.planJaune)
    }

    private fun image(t: Toile, chemin: String, legende: String, z: Zone, jaune: Boolean) {
        val ok = chemin.isNotBlank() && t.image(chemin, z.x + 2, z.y + 2, z.w - 4, z.h - 4)
        if (!ok) boite(t, "$legende : aucune image", z, 9f, false, Aligne.CENTRE, GRIS_TEXTE)
        if (jaune) t.rect(z.x, z.y, z.w, z.h, null, JAUNE, 4f)
        t.rect(z.x, z.y, z.w, z.h, null, BORDURE, 0.8f)
        // Légende en haut à gauche, sur fond blanc.
        val tl = t.largeurTexte(legende, 7f, true) + 6f
        t.rect(z.x, z.y, tl, 10f, BLANC, BORDURE, 0.5f)
        t.texte(legende, z.x + 3f, z.y + 7.5f, 7f, true, MARINE)
    }

    private fun tableauBrides(t: Toile, d: DonneesFiche, brides: List<LigneBride>, ligneEnTete: Int) {
        val colonnes = ChampsBride.colonnesFiche
        val ze = Zone(x(1), y(ligneEnTete), x(3) - x(1), 30f * sy)
        cellule(t, ze, "Bride", fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)
        colonnes.forEachIndexed { i, c ->
            cellule(t, Zone(x(3 + i), ze.y, x(4 + i) - x(3 + i), ze.h), c.libelle, fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)
        }
        cellule(t, Zone(x(14), ze.y, x(18) - x(14), ze.h), "Commentaire", fond = MARINE, gras = true, couleur = BLANC, aligne = Aligne.CENTRE)

        val hLigne = 15f * sy
        val y0 = ze.y + ze.h
        for (i in 0 until BRIDES_PAR_PAGE) {
            val b = brides.getOrNull(i)
            val yl = y0 + i * hLigne
            val libelle = when {
                b == null -> ""
                b.designation.isBlank() -> b.repere
                else -> "${b.repere} — ${b.designation}"
            }
            cellule(t, Zone(x(1), yl, x(3) - x(1), hLigne), libelle, fond = if (b?.reperJaune == true) JAUNE else LIBELLE, gras = true)
            colonnes.indices.forEach { c ->
                val jaune = b?.jaunes?.getOrNull(c) == true
                cellule(t, Zone(x(3 + c), yl, x(4 + c) - x(3 + c), hLigne), b?.valeurs?.getOrNull(c).orEmpty(),
                    fond = if (jaune) JAUNE else null, aligne = Aligne.CENTRE)
            }
        }
        // Commentaire (cellules N:Q fusionnées sur les 5 lignes de brides).
        val zc = Zone(x(14), y0, x(18) - x(14), hLigne * BRIDES_PAR_PAGE)
        val jaune = ChampsFiche.COMMENTAIRE in d.jaunes
        cellule(t, zc, d.valeurs[ChampsFiche.COMMENTAIRE].orEmpty(), fond = if (jaune) JAUNE else null, haut = true)
    }

    // --- Outils de dessin -----------------------------------------------------------------

    private enum class Aligne { GAUCHE, CENTRE }

    private fun cadre(t: Toile, z: Zone) = t.rect(z.x, z.y, z.w, z.h, null, BORDURE, 0.5f)

    private fun cellule(
        t: Toile, z: Zone, texte: String, fond: Int? = null, gras: Boolean = false, couleur: Int = NOIR,
        aligne: Aligne = Aligne.GAUCHE, haut: Boolean = false
    ) {
        t.rect(z.x, z.y, z.w, z.h, fond, BORDURE, 0.5f)
        boite(t, texte, z, 8f, gras, aligne, couleur, haut)
    }

    /** Texte ajusté dans la zone : retour à la ligne, puis réduction de la police si nécessaire. */
    private fun boite(
        t: Toile, texte: String, z: Zone, tailleMax: Float, gras: Boolean, aligne: Aligne,
        couleur: Int = NOIR, haut: Boolean = false
    ) {
        if (texte.isBlank()) return
        val pad = 2f
        val wDispo = z.w - 2 * pad
        val hDispo = z.h - 2 * pad
        var taille = tailleMax
        var lignes: List<String>
        while (true) {
            lignes = couper(t, texte.trim(), wDispo, taille, gras)
            // On réduit la police tant que le texte déborde ou qu'un mot a dû être coupé en deux.
            val motCoupe = texte.split(' ', '\n').any { it.isNotEmpty() && t.largeurTexte(it, taille, gras) > wDispo }
            if ((lignes.size * taille * 1.15f <= hDispo && !motCoupe) || taille <= 4.5f) break
            taille -= 0.5f
        }
        val interligne = taille * 1.15f
        val maxLignes = maxOf(1, (hDispo / interligne).toInt())
        if (lignes.size > maxLignes) {
            lignes = lignes.take(maxLignes).toMutableList().also { it[it.size - 1] = it.last().trimEnd() + "…" }
        }
        val hTexte = lignes.size * interligne
        var yBase = (if (haut) z.y + pad else z.y + (z.h - hTexte) / 2) + taille * 0.95f
        for (l in lignes) {
            val xl = if (aligne == Aligne.CENTRE) z.x + (z.w - t.largeurTexte(l, taille, gras)) / 2 else z.x + pad
            t.texte(l, xl, yBase, taille, gras, couleur)
            yBase += interligne
        }
    }

    private fun couper(t: Toile, texte: String, w: Float, taille: Float, gras: Boolean): List<String> {
        val result = mutableListOf<String>()
        for (paragraphe in texte.split('\n')) {
            var courant = ""
            for (mot in paragraphe.split(' ').filter { it.isNotEmpty() }) {
                val essai = if (courant.isEmpty()) mot else "$courant $mot"
                if (t.largeurTexte(essai, taille, gras) <= w) {
                    courant = essai
                    continue
                }
                if (courant.isNotEmpty()) result.add(courant)
                // Mot plus long que la largeur : coupé au caractère.
                var reste = mot
                while (t.largeurTexte(reste, taille, gras) > w && reste.length > 1) {
                    var n = reste.length - 1
                    while (n > 1 && t.largeurTexte(reste.substring(0, n), taille, gras) > w) n--
                    result.add(reste.substring(0, n))
                    reste = reste.substring(n)
                }
                courant = reste
            }
            result.add(courant)
        }
        return result
    }
}
