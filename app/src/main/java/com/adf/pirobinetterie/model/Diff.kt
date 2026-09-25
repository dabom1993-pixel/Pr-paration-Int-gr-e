package com.adf.pirobinetterie.model

/**
 * Différences entre deux états d'un item : ce qui doit apparaître sur fond jaune.
 * - [champs] : clés Suivi modifiées ;
 * - [cellules] : id de bride -> clés Matos modifiées ;
 * - [bridesAjoutees] : ids des brides absentes de l'état précédent (ligne entière en jaune) ;
 * - [bridesSupprimees] : brides disparues (n'apparaissent plus, mentionnées à l'écran).
 */
class Diff(
    val champs: Set<String>,
    val cellules: Map<String, Set<String>>,
    val bridesAjoutees: Set<String>,
    val bridesSupprimees: List<Bride>,
    val photo: Boolean,
    val plan: Boolean
) {
    fun estVide(): Boolean = champs.isEmpty() && cellules.isEmpty() && bridesAjoutees.isEmpty() &&
        bridesSupprimees.isEmpty() && !photo && !plan

    fun champ(cle: String): Boolean = cle in champs

    fun cellule(brideId: String, cle: String): Boolean =
        brideId in bridesAjoutees || cellules[brideId]?.contains(cle) == true

    companion object {
        val VIDE = Diff(emptySet(), emptyMap(), emptySet(), emptyList(), false, false)

        private fun egal(a: String?, b: String?): Boolean = a.orEmpty().trim() == b.orEmpty().trim()

        fun entre(avant: Etat, apres: Etat): Diff {
            val champs = (avant.v.keys + apres.v.keys).filter { !egal(avant.v[it], apres.v[it]) }.toSet()

            val avantParId = avant.brides.associateBy { it.id }
            val apresIds = apres.brides.map { it.id }.toSet()
            val cellules = linkedMapOf<String, Set<String>>()
            val ajoutees = linkedSetOf<String>()
            for (b in apres.brides) {
                val a = avantParId[b.id]
                if (a == null) {
                    ajoutees.add(b.id)
                    continue
                }
                val cles = (a.v.keys + b.v.keys).filter { !egal(a.v[it], b.v[it]) }.toSet()
                if (cles.isNotEmpty()) cellules[b.id] = cles
            }
            val supprimees = avant.brides.filter { it.id !in apresIds }

            return Diff(
                champs, cellules, ajoutees, supprimees,
                photo = !egal(avant.photo, apres.photo),
                plan = !egal(avant.plan, apres.plan)
            )
        }
    }
}
