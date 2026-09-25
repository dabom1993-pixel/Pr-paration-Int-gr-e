package com.adf.pirobinetterie.model

/**
 * Un champ affiché sur la fiche. [cle] = libellé normalisé de la colonne Excel correspondante
 * (voir [cle]), [liste] = clé de la liste déroulante de l'onglet DATA proposée à la saisie.
 */
class Champ(val cle: String, val libelle: String, val liste: String? = null)

/** Clés des listes de l'onglet DATA (ligne 5 : Type, Description, DN, Série...). */
object Listes {
    const val TYPE = "type"
    const val DESCRIPTION = "description"
    const val DN = "dn"
    const val SERIE = "serie"
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

    val donneesTechniques = listOf(
        Champ(UNITE, "Unité / Zone"),
        Champ(TYPE, "Famille", Listes.TYPE),
        Champ("hauteur", "Hauteur / Niveau"),
        Champ("lienautreitem", "Item à côté"),
        Champ("nligne", "N° Ligne"),
        Champ("nopergraph", "N° Opergraph"),
        Champ("specligne", "Classe tuyauterie")
    )

    val besoins = listOf(
        Champ("echaf", "Besoin echaf", Listes.OUI_NON),
        Champ("calo", "Besoin calo", Listes.OUI_NON),
        Champ("levage", "Besoin Levage", Listes.LEVAGE),
        Champ("besoinpotence", "Besoin potence", Listes.OUI_NON)
    )

    val travaux = listOf(
        Champ("travaux", "Travaux", Listes.DESCRIPTION),
        Champ("nettoyage", "Nettoyage", Listes.OUI_NON),
        Champ("tracing", "Traçage", Listes.TRACAGE),
        Champ("boitearessort", "Boite à Ressort", Listes.OUI_NON),
        Champ("ariepi", "EPI"),
        Champ("raat", "RAAT Plomb/Amiante", Listes.OUI_NON)
    )

    val commentaire = Champ(COMMENTAIRE, "Commentaire")

    /**
     * Champs de la Fiche absents de l'onglet Suivi d'origine : ajoutés en fin de ligne d'en-têtes
     * de Suivi à l'export (clé -> libellé écrit dans l'Excel), seulement s'ils sont renseignés.
     */
    val colonnesAjoutees = linkedMapOf(
        "nligne" to "N° Ligne",
        "nopergraph" to "N° Opergraph",
        "besoinpotence" to "Besoin potence"
    )

    /** Toutes les clés affichées sur la Fiche (les autres colonnes Suivi vont dans "Autres infos"). */
    val clesFiche: Set<String> =
        (donneesTechniques + besoins + travaux + commentaire).map { it.cle }.toSet() + setOf(NOM, REV)

    /** Liste DATA proposée pour une colonne Suivi hors Fiche. */
    fun listePour(cleSuivi: String): String? = when (cleSuivi) {
        "equipe" -> Listes.EQUIPE
        "somf" -> Listes.OUI_NON
        else -> (donneesTechniques + besoins + travaux).firstOrNull { it.cle == cleSuivi }?.liste
    }
}

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
    const val RONDELLE = "rondelle"
    const val LG = "lgb"
    const val DIAM = "diamb"
    const val NEUF = "neufb"
    const val QTE = "qteb"
    const val OBTURATION = "obturation"
    const val MATIERE_TIGE = "matiereb"

    /** Colonnes du tableau des brides de la Fiche, dans l'ordre (colonnes C à M). */
    val colonnesFiche = listOf(
        Champ(DN, "DN", Listes.DN),
        Champ(PN, "PN", Listes.SERIE),
        Champ(QTE, "Qté TF"),
        Champ(MATIERE_JOINT, "Matière Jt", Listes.MATIERE_JOINT),
        Champ(OBTURATION, "Obturation", Listes.OBTURATEUR),
        Champ(SERRAGE, "Serrage"),
        Champ(LG, "Lg TF"),
        Champ(DIAM, "Diam TF", Listes.DIAM_TIGE),
        Champ(MATIERE_TIGE, "Matière TF", Listes.MATIERE_TIGE),
        Champ(RONDELLE, "Rondelle", Listes.OUI_NON),
        Champ(NEUF, "Neuf TF", Listes.OUI_NON)
    )

    /** Colonnes éditables sur la tablette (colonnes de la Fiche + repère/désignation). */
    val colonnesEdition = listOf(Champ(REP, "Rep."), Champ(DESIGNATION, "Désignation")) + colonnesFiche

    /** Libellés par défaut de l'onglet Matos (si l'onglet importé n'a pas la colonne). */
    val libellesMatos = linkedMapOf(
        UNITE to "Unité", FAMILLE to "Famille", ITEM to "Item", REP to "Rep.", DESIGNATION to "Désignation",
        DN to "DN", PN to "PN", SERRAGE to "Serrage", MATIERE_JOINT to "MatièreJ", RONDELLE to "Rondelle",
        LG to "LgB", DIAM to "DiamB", NEUF to "NeufB", QTE to "QteB", OBTURATION to "Obturation",
        MATIERE_TIGE to "MatièreB"
    )

    fun estOui(valeur: String): Boolean = cle(valeur) in setOf("o", "oui", "y", "yes", "x", "1")
}
