package com.adf.pirobinetterie.model

/**
 * Un champ affiché sur la fiche. [cle] = libellé normalisé de la colonne Excel correspondante
 * (voir [cle]), [liste] = clé de la liste déroulante de l'onglet DATA proposée à la saisie,
 * [court] = libellé imprimé dans le PDF (celui de l'onglet Fiche).
 */
class Champ(val cle: String, val libelle: String, val liste: String? = null, val court: String = libelle)

/** Clés des listes de l'onglet DATA (ligne 5 : Type, Description, DN, Série...). */
object Listes {
    const val TYPE = "type"
    const val DESCRIPTION = "description"
    const val DN = "dn"
    const val SERIE = "serie"
    const val FACE = "face"
    const val MATIERE_JOINT = "matierejt"
    const val EQUIPE = "equipe"
    const val OBTURATEUR = "obturateur"
    const val OUI_NON = "ouinon"
    /** Colonne "Ø tige" (M 14, M 16...). */
    const val DIAM_TIGE = "diamtige"
    /** Colonne "Tige" (B7, H7, L7...) = matière de la boulonnerie. */
    const val MATIERE_TIGE = "tige"
    const val LEVAGE = "levage"
    const val TRACAGE = "tracage"
    const val RONDELLE = "rondelle"
}

/** Correspondance entre la Fiche et les colonnes de l'onglet Suivi. */
object ChampsFiche {
    const val NOM = "nom"
    const val REV = "rev"
    const val UNITE = "unite"
    const val TYPE = "type"
    const val COMMENTAIRE = "commentaire"

    /** Lignes 26 à 33 de la Fiche. */
    val donneesTechniques = listOf(
        Champ(UNITE, "Unité / Zone"),
        Champ("chronoiso", "Chrono ISO"),
        Champ(TYPE, "Type", Listes.TYPE),
        Champ("travaux", "Travaux", Listes.DESCRIPTION),
        Champ("equipementmaitre", "Equipement Maitre"),
        Champ("nligne", "N° Ligne"),
        Champ("nopergraph", "N° Opergraph"),
        Champ("specligne", "Classe tuyauterie")
    )

    /** Lignes 35 à 40 de la Fiche. */
    val besoins = listOf(
        Champ("hauteur", "Hauteur / Niveau"),
        Champ("poids", "Poids"),
        Champ("echaf", "Besoin echaf", Listes.OUI_NON),
        Champ("calo", "Besoin calo", Listes.OUI_NON),
        Champ("levage", "Besoin Levage", Listes.LEVAGE),
        Champ("potence", "Besoin potence", Listes.OUI_NON)
    )

    /** Lignes 42 à 45 de la Fiche. */
    val divers = listOf(
        Champ("tracing", "Traçage", Listes.TRACAGE),
        Champ("boitearessort", "Boite à Ressort", Listes.OUI_NON),
        Champ("somf", "SOMF", Listes.OUI_NON),
        Champ("ariepi", "EPI")
    )

    val commentaire = Champ(COMMENTAIRE, "Commentaire")

    /**
     * Champs de la Fiche qui peuvent manquer dans un onglet Suivi plus ancien : ajoutés en fin de
     * ligne d'en-têtes à l'export (clé -> libellé écrit dans l'Excel), seulement s'ils sont renseignés.
     */
    val colonnesAjoutees = linkedMapOf(
        "chronoiso" to "Chrono ISO",
        "equipementmaitre" to "Equipement Maitre",
        "nligne" to "N° Ligne",
        "nopergraph" to "N° Opergraph",
        "specligne" to "SPEC ligne",
        "hauteur" to "HAUTEUR",
        "poids" to "POIDS",
        "potence" to "Potence",
        "somf" to "SOMF"
    )

    /** Toutes les clés affichées sur la Fiche (les autres colonnes Suivi vont dans "Autres infos"). */
    val clesFiche: Set<String> =
        (donneesTechniques + besoins + divers + commentaire).map { it.cle }.toSet() + setOf(NOM, REV)

    /** Liste DATA proposée pour une colonne Suivi hors Fiche. */
    fun listePour(cleSuivi: String): String? = when (cleSuivi) {
        "equipe" -> Listes.EQUIPE
        "nettoyage", "raat" -> Listes.OUI_NON
        else -> (donneesTechniques + besoins + divers).firstOrNull { it.cle == cleSuivi }?.liste
    }

    /** Libellés de colonnes Suivi ramenés à une clé unique ("Item" et "Nom" = nom de l'item). */
    fun alias(cle: String): String = when (cle) {
        "item" -> NOM
        "besoinpotence" -> "potence"
        else -> cle
    }
}

/** Groupe de colonnes du tableau des brides de la Fiche (JOINT, BRIDE, TIGES FILETÉES, RAAT). */
class GroupeColonnes(val libelle: String, val colonnes: List<Champ>)

/** Colonnes de l'onglet Matos (une ligne par bride) et leur libellé sur la Fiche. */
object ChampsBride {
    const val UNITE = "unite"
    const val FAMILLE = "famille"
    const val ITEM = "item"
    const val REP = "rep"
    const val DESIGNATION = "designation"
    const val DN = "dn"
    const val PN = "pn"
    const val SERRAGE = "serrage"
    const val MATIERE_JOINT = "matierej"
    const val FACE = "face"
    const val RONDELLE = "rondelle"
    const val LG = "lgb"
    const val DIAM = "diamb"
    const val NEUF = "neufb"
    const val QTE = "qteb"
    const val OBTURATION = "obturation"
    const val MATIERE_TIGE = "matiereb"
    const val RAAT = "raat"

    /** Tableau des brides de la Fiche (lignes 47-48), colonnes C à M. */
    val groupesFiche = listOf(
        GroupeColonnes("JOINT", listOf(
            Champ(DN, "DN", Listes.DN),
            Champ(PN, "PN", Listes.SERIE),
            Champ(MATIERE_JOINT, "Matière joint", Listes.MATIERE_JOINT, court = "Matière")
        )),
        GroupeColonnes("BRIDE", listOf(
            Champ(FACE, "Face", Listes.FACE),
            Champ(OBTURATION, "Obtur", Listes.OBTURATEUR),
            Champ(SERRAGE, "Serrage")
        )),
        GroupeColonnes("TIGES FILETÉES", listOf(
            Champ(LG, "Lg tiges", court = "Lg"),
            Champ(DIAM, "Diam tiges", Listes.DIAM_TIGE, court = "Diam"),
            Champ(MATIERE_TIGE, "Matière tiges", Listes.MATIERE_TIGE, court = "Matière"),
            Champ(RONDELLE, "Rondelle", Listes.OUI_NON)
        )),
        GroupeColonnes("RAAT", listOf(Champ(RAAT, "RAAT", Listes.OUI_NON)))
    )

    val colonnesFiche: List<Champ> = groupesFiche.flatMap { it.colonnes }

    /** Colonnes éditables sur la tablette (colonnes de la Fiche + repère/désignation). */
    val colonnesEdition = listOf(Champ(REP, "Rep."), Champ(DESIGNATION, "Désignation")) + colonnesFiche

    /**
     * Libellés de l'onglet Matos ramenés à une clé unique : les deux versions du fichier
     * ("Famille"/"Type", "MatièreJ"/"Matière", "LgB"/"Lg"...) donnent les mêmes clés.
     */
    fun alias(cle: String): String = when (cle) {
        "unitezone" -> UNITE
        "type" -> FAMILLE
        "matiere" -> MATIERE_JOINT
        "matiere2" -> MATIERE_TIGE
        "obtur" -> OBTURATION
        "lg" -> LG
        "diam" -> DIAM
        else -> cle
    }

    fun estOui(valeur: String): Boolean = cle(valeur) in setOf("o", "oui", "y", "yes", "x", "1")
}
