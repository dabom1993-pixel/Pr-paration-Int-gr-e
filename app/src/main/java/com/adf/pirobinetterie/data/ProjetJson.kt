package com.adf.pirobinetterie.data

import com.adf.pirobinetterie.model.Abaque
import com.adf.pirobinetterie.model.Bride
import com.adf.pirobinetterie.model.EnTete
import com.adf.pirobinetterie.model.Etat
import com.adf.pirobinetterie.model.Item
import com.adf.pirobinetterie.model.LigneAbaque
import com.adf.pirobinetterie.model.Projet
import com.adf.pirobinetterie.model.Revision
import org.json.JSONArray
import org.json.JSONObject

/** Sauvegarde du projet sur la tablette : un simple fichier JSON (fonctionne hors connexion). */
object ProjetJson {

    private const val VERSION = 1

    fun ecrire(p: Projet): String = JSONObject().apply {
        put("version", VERSION)
        put("fichierSource", p.fichierSource)
        put("enTete", enTete(p.enTete))
        put("enTeteInitial", enTete(p.enTeteInitial))
        put("items", JSONArray().apply { p.items.forEach { put(item(it)) } })
        put("orphelines", brides(p.bridesOrphelines))
        put("listes", JSONObject().apply { p.listes.forEach { (k, v) -> put(k, JSONArray(v)) } })
        put("abaque", JSONArray().apply {
            p.abaque.lignes.forEach { l ->
                put(JSONObject().apply {
                    put("dn", l.dn); put("serie", l.serie); put("nb", l.nbTiges)
                    put("diam", l.diametre); put("rf", l.longueurRf); put("rtj", l.longueurRtj)
                })
            }
        })
        put("colonnesSuivi", map(p.colonnesSuivi))
        put("colonnesMatos", map(p.colonnesMatos))
    }.toString()

    fun lire(texte: String): Projet {
        val o = JSONObject(texte)
        val listes = linkedMapOf<String, List<String>>()
        o.optJSONObject("listes")?.let { l -> l.keys().forEach { k -> listes[k] = strings(l.getJSONArray(k)) } }
        val abaque = o.optJSONArray("abaque")?.let { a ->
            Abaque((0 until a.length()).map { i ->
                val l = a.getJSONObject(i)
                LigneAbaque(l.getDouble("dn"), l.getInt("serie"), l.optString("nb"), l.optString("diam"), l.optString("rf"), l.optString("rtj"))
            })
        } ?: Abaque.VIDE
        return Projet(
            fichierSource = o.optString("fichierSource"),
            enTete = enTete(o.getJSONObject("enTete")),
            enTeteInitial = enTete(o.getJSONObject("enTeteInitial")),
            items = o.getJSONArray("items").let { a -> (0 until a.length()).map { item(a.getJSONObject(it)) }.toMutableList() },
            bridesOrphelines = brides(o.optJSONArray("orphelines") ?: JSONArray()),
            listes = listes,
            abaque = abaque,
            colonnesSuivi = map(o.getJSONArray("colonnesSuivi")),
            colonnesMatos = map(o.getJSONArray("colonnesMatos"))
        )
    }

    private fun enTete(e: EnTete) = JSONObject().apply {
        put("client", e.client); put("lieu", e.lieu); put("unite", e.unite); put("annee", e.annee)
    }

    private fun enTete(o: JSONObject) =
        EnTete(o.optString("client"), o.optString("lieu"), o.optString("unite"), o.optString("annee"))

    private fun item(i: Item) = JSONObject().apply {
        put("nom", i.nom)
        put("initial", etat(i.initial))
        put("v", map(i.v))
        put("brides", brides(i.brides))
        put("photo", i.photo)
        put("plan", i.plan)
        put("revisions", JSONArray().apply {
            i.revisions.forEach { r ->
                put(JSONObject().apply {
                    put("num", r.num); put("date", r.date); put("objet", r.objet); put("etat", etat(r.etat))
                })
            }
        })
    }

    private fun item(o: JSONObject): Item {
        val revisions = o.optJSONArray("revisions")?.let { a ->
            (0 until a.length()).map { idx ->
                val r = a.getJSONObject(idx)
                Revision(r.getInt("num"), r.optString("date"), r.optString("objet"), etat(r.getJSONObject("etat")))
            }
        }.orEmpty()
        return Item(
            nom = o.getString("nom"),
            initial = etat(o.getJSONObject("initial")),
            v = map(o.getJSONArray("v")),
            brides = brides(o.getJSONArray("brides")).toMutableList(),
            revisions = revisions.toMutableList(),
            photo = o.optString("photo"),
            plan = o.optString("plan")
        )
    }

    private fun etat(e: Etat) = JSONObject().apply {
        put("v", map(e.v)); put("brides", brides(e.brides)); put("photo", e.photo); put("plan", e.plan)
    }

    private fun etat(o: JSONObject) =
        Etat(map(o.getJSONArray("v")), brides(o.getJSONArray("brides")), o.optString("photo"), o.optString("plan"))

    private fun brides(liste: List<Bride>) = JSONArray().apply {
        liste.forEach { b -> put(JSONObject().apply { put("id", b.id); put("v", map(b.v)) }) }
    }

    private fun brides(a: JSONArray): List<Bride> = (0 until a.length()).map { i ->
        val b = a.getJSONObject(i)
        Bride(b.getString("id"), map(b.getJSONArray("v")))
    }

    /** Tableau de paires [clé, valeur] : l'ordre des colonnes est ainsi toujours conservé. */
    private fun map(m: Map<String, String>) = JSONArray().apply { m.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } }

    private fun map(a: JSONArray): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        for (i in 0 until a.length()) {
            val paire = a.getJSONArray(i)
            result[paire.getString(0)] = paire.optString(1)
        }
        return result
    }

    private fun strings(a: JSONArray): List<String> = (0 until a.length()).map { a.optString(it) }
}
