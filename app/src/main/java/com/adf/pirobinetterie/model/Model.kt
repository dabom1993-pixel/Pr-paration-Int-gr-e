package com.adf.pirobinetterie.model

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Clé normalisée d'un libellé de colonne Excel : sans accents, en minuscules, sans espaces ni
 * ponctuation ("Rép." -> "rep", "N° Ligne" -> "nligne"). Les colonnes sont toujours retrouvées
 * par leur libellé et jamais par leur lettre : les onglets peuvent gagner des colonnes.
 */
fun cle(libelle: String): String {
    val sansAccents = Normalizer.normalize(libelle, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    return sansAccents.lowercase(Locale.FRANCE).replace(Regex("[^a-z0-9]"), "")
}

/** Une bride = une ligne de l'onglet Matos. [v] : clé de colonne Matos -> valeur. */
class Bride(
    val id: String = UUID.randomUUID().toString(),
    val v: MutableMap<String, String> = linkedMapOf()
) {
    operator fun get(key: String): String = v[key].orEmpty()
    operator fun set(key: String, value: String) { v[key] = value }
    fun copie(): Bride = Bride(id, LinkedHashMap(v))
}

/** État complet d'un item à un instant donné (données Suivi + brides + images). */
class Etat(
    val v: Map<String, String>,
    val brides: List<Bride>,
    val photo: String,
    val plan: String
) {
    fun memeContenu(autre: Etat): Boolean = Diff.entre(autre, this).estVide()
}

/** Révision validée : figée au moment du clic sur "Valider". */
class Revision(
    val num: Int,
    val date: String,
    val objet: String,
    val etat: Etat
)

/** Un item = une ligne de l'onglet Suivi (clé : colonne "Nom"). */
class Item(
    val nom: String,
    /** Valeurs lues dans l'Excel à l'import, pour n'écrire à l'export que ce qui a changé. */
    var initial: Etat,
    val v: MutableMap<String, String>,
    val brides: MutableList<Bride>,
    val revisions: MutableList<Revision> = mutableListOf(),
    var photo: String = "",
    var plan: String = ""
) {
    operator fun get(key: String): String = v[key].orEmpty()
    operator fun set(key: String, value: String) { v[key] = value }

    fun etatCourant(): Etat = Etat(LinkedHashMap(v), brides.map { it.copie() }, photo, plan)

    val derniere: Revision? get() = revisions.maxByOrNull { it.num }

    /** Numéro de la révision en cours (celle que "Valider" va créer). */
    val numEnCours: Int get() = derniere?.num?.plus(1) ?: 0

    /** État de référence pour le surlignage à l'écran : dernière révision validée. */
    fun reference(): Etat? = derniere?.etat

    /** true si des modifications n'ont pas encore été validées. */
    fun modifieDepuisValidation(): Boolean {
        val ref = derniere?.etat ?: return !initial.memeContenu(etatCourant())
        return !ref.memeContenu(etatCourant())
    }

    /** État exporté dans l'Excel : dernière révision validée, sinon données d'origine. */
    fun etatExporte(): Etat = derniere?.etat ?: initial

    /** Différences surlignées en jaune pour la révision [num] (révision n comparée à n-1). */
    fun diffRevision(num: Int): Diff {
        val rev = revisions.firstOrNull { it.num == num } ?: return Diff.VIDE
        val precedente = revisions.filter { it.num < num }.maxByOrNull { it.num } ?: return Diff.VIDE
        return Diff.entre(precedente.etat, rev.etat)
    }

    fun statut(): Statut = when {
        revisions.isEmpty() -> if (modifieDepuisValidation()) Statut.EN_COURS else Statut.A_VALIDER
        modifieDepuisValidation() -> Statut.REVISION_EN_COURS
        else -> Statut.VALIDE
    }
}

enum class Statut(val libelle: String) {
    A_VALIDER("À valider"),
    EN_COURS("Rev 0 en cours"),
    REVISION_EN_COURS("Révision en cours"),
    VALIDE("Validé")
}

/** En-tête du projet (onglet Instruction : Client / Lieu / Unité / Année). */
class EnTete(
    var client: String = "",
    var lieu: String = "",
    var unite: String = "",
    var annee: String = ""
) {
    fun titre(): String {
        val l1 = listOf(client, lieu).filter { it.isNotBlank() }.joinToString(" - ")
        val l2 = listOf(unite, annee).filter { it.isNotBlank() }.joinToString(" ")
        return listOf(l1, l2).filter { it.isNotBlank() }.joinToString("\n")
    }
    fun copie() = EnTete(client, lieu, unite, annee)
}

class Projet(
    /** Nom du fichier Excel importé (le fichier lui-même est copié dans l'app). */
    val fichierSource: String,
    val enTete: EnTete,
    val enTeteInitial: EnTete,
    val items: MutableList<Item>,
    /** Brides de l'onglet Matos dont l'item n'existe pas dans Suivi : conservées telles quelles. */
    val bridesOrphelines: List<Bride>,
    /** Listes déroulantes de l'onglet DATA (clé = libellé normalisé de la colonne). */
    val listes: Map<String, List<String>>,
    val abaque: Abaque,
    /** Colonnes de l'onglet Suivi (clé -> libellé), dans l'ordre de la feuille. */
    val colonnesSuivi: LinkedHashMap<String, String>,
    /** Colonnes de l'onglet Matos (clé -> libellé), dans l'ordre de la feuille. */
    val colonnesMatos: LinkedHashMap<String, String>
) {
    fun item(nom: String): Item? = items.firstOrNull { it.nom == nom }
}
