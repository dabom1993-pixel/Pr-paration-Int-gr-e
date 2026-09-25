package com.adf.pirobinetterie.model

/** Une ligne de l'abaque de boulonnerie (onglet DATA, bloc "ABBAQUE"). */
class LigneAbaque(
    val dn: Double,
    val serie: Int,
    val nbTiges: String,
    val diametre: String,
    val longueurRf: String,
    val longueurRtj: String
)

/**
 * Abaque de boulonnerie : DN (pouces) + série (150, 300...) -> nombre de tiges, diamètre, longueur.
 * Reproduit la recherche VLOOKUP(DN&Série) des macros de l'Excel.
 */
class Abaque(val lignes: List<LigneAbaque>) {

    fun cherche(dn: String, pn: String): LigneAbaque? {
        val d = parseDn(dn) ?: return null
        val s = parseSerie(pn) ?: return null
        return lignes.firstOrNull { Math.abs(it.dn - d) < 1e-6 && it.serie == s && it.nbTiges.isNotBlank() }
    }

    /**
     * Remplit Qté / Diamètre / Longueur de [bride] d'après l'abaque (longueur RF, + [rondelleMm]
     * si la bride a des rondelles). Retourne false si le couple DN/PN est absent de l'abaque.
     */
    fun calcule(bride: Bride, rondelleMm: Double): Boolean {
        val ligne = cherche(bride[ChampsBride.DN], bride[ChampsBride.PN]) ?: return false
        bride[ChampsBride.QTE] = ligne.nbTiges
        bride[ChampsBride.DIAM] = ligne.diametre
        val lg = ligne.longueurRf.replace(',', '.').toDoubleOrNull()
        bride[ChampsBride.LG] = when {
            lg == null -> ligne.longueurRf
            ChampsBride.estOui(bride[ChampsBride.RONDELLE]) -> nombre(lg + rondelleMm)
            else -> nombre(lg)
        }
        return true
    }

    companion object {
        val VIDE = Abaque(emptyList())

        /** "2", "2\"", "0,5", "1 1/2", "3/4", "DN 4" -> valeur en pouces. */
        fun parseDn(texte: String): Double? {
            val t = texte.replace(',', '.').replace(Regex("[^0-9./ ]"), " ").trim().replace(Regex("\\s+"), " ")
            if (t.isEmpty()) return null
            val parties = t.split(" ")
            var total = 0.0
            for (p in parties) {
                total += if ('/' in p) {
                    val (a, b) = p.split("/", limit = 2).map { it.toDoubleOrNull() }
                    if (a == null || b == null || b == 0.0) return null
                    a / b
                } else p.toDoubleOrNull() ?: return null
            }
            return total
        }

        /** "150", "150#", "Class 300", "300 lbs" -> 150 / 300. */
        fun parseSerie(texte: String): Int? =
            Regex("\\d+").find(texte.replace(Regex("[.,]0+\\b"), ""))?.value?.toIntOrNull()

        /** 60.0 -> "60", 62.5 -> "62.5". */
        fun nombre(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
            else java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
    }
}
