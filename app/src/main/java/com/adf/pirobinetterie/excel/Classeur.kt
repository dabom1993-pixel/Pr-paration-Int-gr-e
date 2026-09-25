package com.adf.pirobinetterie.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * Lecture d'un classeur Excel (.xlsx / .xlsm). Un tel fichier est une archive ZIP de fichiers
 * XML (format OOXML) : on le lit directement, sans bibliothèque externe, et sans jamais
 * exécuter les macros.
 */
class Classeur(fichier: File) : Closeable {

    private val zip = ZipFile(fichier)
    private val sharedStrings: List<String> by lazy { lireSharedStrings() }
    private val formatsDates: Set<Int> by lazy { lireStylesDates() }

    /** Onglets du classeur : nom -> chemin du XML dans l'archive (ex. "xl/worksheets/sheet3.xml"). */
    val onglets: LinkedHashMap<String, String> by lazy { lireOnglets() }

    fun cheminOnglet(nom: String): String? =
        onglets.entries.firstOrNull { it.key.trim().equals(nom, ignoreCase = true) }?.value

    fun lireOnglet(nom: String): Feuille? {
        val chemin = cheminOnglet(nom) ?: return null
        val entry = zip.getEntry(chemin) ?: return null
        val handler = FeuilleHandler()
        zip.getInputStream(entry).use { parse(it, handler) }
        return Feuille(handler.lignes)
    }

    override fun close() = zip.close()

    // --- Lecture interne ------------------------------------------------------------------

    private fun lireOnglets(): LinkedHashMap<String, String> {
        val cibles = mutableMapOf<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            zip.getInputStream(e).use {
                parse(it, object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                        if (local(qName) == "Relationship") {
                            val id = attrs.getValue("Id") ?: return
                            val target = attrs.getValue("Target") ?: return
                            cibles[id] = resoudreCible(target)
                        }
                    }
                })
            }
        }
        val result = LinkedHashMap<String, String>()
        zip.getEntry("xl/workbook.xml")?.let { e ->
            zip.getInputStream(e).use {
                parse(it, object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                        if (local(qName) == "sheet") {
                            val nom = attrs.getValue("name") ?: return
                            val rid = attrs.getValue("r:id") ?: return
                            cibles[rid]?.let { result[nom] = it }
                        }
                    }
                })
            }
        }
        return result
    }

    private fun lireSharedStrings(): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val result = ArrayList<String>()
        zip.getInputStream(entry).use {
            parse(it, object : DefaultHandler() {
                private val courant = StringBuilder()
                private var dansT = false
                private var dansPhonetique = false
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    when (local(qName)) {
                        "si" -> courant.setLength(0)
                        "rPh" -> dansPhonetique = true
                        "t" -> dansT = !dansPhonetique
                    }
                }
                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (local(qName)) {
                        "si" -> result.add(courant.toString())
                        "rPh" -> dansPhonetique = false
                        "t" -> dansT = false
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (dansT) courant.append(ch, start, length)
                }
            })
        }
        return result
    }

    /** Index des styles de cellule (cellXfs) dont le format numérique est une date. */
    private fun lireStylesDates(): Set<Int> {
        val entry = zip.getEntry("xl/styles.xml") ?: return emptySet()
        val formatsPerso = mutableMapOf<Int, String>()
        val xfFormats = ArrayList<Int>()
        zip.getInputStream(entry).use {
            parse(it, object : DefaultHandler() {
                private var dansCellXfs = false
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    when (local(qName)) {
                        "numFmt" -> {
                            val id = attrs.getValue("numFmtId")?.toIntOrNull() ?: return
                            formatsPerso[id] = attrs.getValue("formatCode").orEmpty()
                        }
                        "cellXfs" -> dansCellXfs = true
                        "xf" -> if (dansCellXfs) xfFormats.add(attrs.getValue("numFmtId")?.toIntOrNull() ?: 0)
                    }
                }
                override fun endElement(uri: String?, localName: String?, qName: String) {
                    if (local(qName) == "cellXfs") dansCellXfs = false
                }
            })
        }
        fun estDate(id: Int): Boolean {
            if (id in 14..22 || id in 45..47) return true
            val code = formatsPerso[id] ?: return false
            val sansTexte = code.replace(Regex("\"[^\"]*\"|\\[[^]]*]|\\\\."), "").lowercase(Locale.ROOT)
            return sansTexte.contains('d') || sansTexte.contains('y') ||
                (sansTexte.contains('m') && !sansTexte.contains('0') && !sansTexte.contains('#'))
        }
        return xfFormats.indices.filter { estDate(xfFormats[it]) }.toSet()
    }

    private inner class FeuilleHandler : DefaultHandler() {
        val lignes = sortedMapOf<Int, java.util.SortedMap<Int, String>>()
        private var ref: String? = null
        private var type: String? = null
        private var style = 0
        private val valeur = StringBuilder()
        private val inline = StringBuilder()
        private var dansV = false
        private var dansT = false
        private var ligneCourante = 0

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            when (local(qName)) {
                "row" -> ligneCourante = attrs.getValue("r")?.toIntOrNull() ?: (ligneCourante + 1)
                "c" -> {
                    ref = attrs.getValue("r")
                    type = attrs.getValue("t")
                    style = attrs.getValue("s")?.toIntOrNull() ?: 0
                    valeur.setLength(0)
                    inline.setLength(0)
                }
                "v" -> dansV = true
                "t" -> dansT = true
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (local(qName)) {
                "v" -> dansV = false
                "t" -> dansT = false
                "c" -> {
                    val r = ref
                    val texte = convertir()
                    if (r != null && texte.isNotEmpty()) {
                        val (col, ligne) = Ref.decoupe(r)
                        lignes.getOrPut(ligne) { sortedMapOf() }[col] = texte
                    }
                    ref = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (dansV) valeur.append(ch, start, length)
            else if (dansT) inline.append(ch, start, length)
        }

        private fun convertir(): String {
            val brut = valeur.toString()
            return when (type) {
                "s" -> brut.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                "inlineStr" -> inline.toString()
                "str" -> brut
                "b" -> if (brut == "1") "VRAI" else "FAUX"
                "e" -> ""
                else -> {
                    val d = brut.toDoubleOrNull() ?: return brut
                    if (style in formatsDates) dateExcel(d) else nombre(d)
                }
            }
        }
    }

    private fun parse(input: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.newSAXParser().parse(input, handler)
    }

    companion object {
        internal fun local(qName: String): String = qName.substringAfter(':')

        /** Cible d'une relation de workbook.xml.rels -> chemin dans l'archive. */
        internal fun resoudreCible(target: String): String =
            if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")

        /** 0.30000000000000004 -> "0.3", 150.0 -> "150". */
        fun nombre(d: Double): String {
            if (d.isNaN() || d.isInfinite()) return d.toString()
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return d.toLong().toString()
            return BigDecimal(d).round(MathContext(12)).stripTrailingZeros().toPlainString()
        }

        /** Numéro de série Excel (système 1900) -> "jj/mm/aaaa" (ou "jj/mm/aaaa hh:mm"). */
        fun dateExcel(serie: Double): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            val jours = Math.floor(serie).toInt()
            cal.add(Calendar.DAY_OF_MONTH, jours)
            val minutes = Math.round((serie - jours) * 24 * 60).toInt()
            cal.add(Calendar.MINUTE, minutes)
            val format = if (minutes == 0) "dd/MM/yyyy" else "dd/MM/yyyy HH:mm"
            return SimpleDateFormat(format, Locale.FRANCE).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(cal.time)
        }
    }
}

/** Contenu d'un onglet : ligne -> (colonne -> texte), numéros à partir de 1. */
class Feuille(val lignes: java.util.SortedMap<Int, java.util.SortedMap<Int, String>>) {
    fun valeur(ligne: Int, col: Int): String = lignes[ligne]?.get(col).orEmpty()
    fun ligne(ligne: Int): Map<Int, String> = lignes[ligne] ?: emptyMap()
}

/** Références de cellules : "AB12" <-> (colonne 28, ligne 12). */
object Ref {
    fun decoupe(ref: String): Pair<Int, Int> {
        val lettres = ref.takeWhile { it.isLetter() }
        return colIndex(lettres) to (ref.substring(lettres.length).toIntOrNull() ?: 0)
    }

    fun colIndex(lettres: String): Int {
        var r = 0
        for (c in lettres.uppercase(Locale.ROOT)) r = r * 26 + (c - 'A' + 1)
        return r
    }

    fun colLettres(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i > 0) {
            val rem = (i - 1) % 26
            sb.insert(0, 'A' + rem)
            i = (i - 1) / 26
        }
        return sb.toString()
    }

    fun de(col: Int, ligne: Int): String = colLettres(col) + ligne
}
