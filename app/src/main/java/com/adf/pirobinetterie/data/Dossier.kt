package com.adf.pirobinetterie.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/**
 * Accès au dossier de travail choisi sur la tablette (ex. Documents/PI_Robinetterie, rempli par
 * câble USB) via le Storage Access Framework : l'utilisateur choisit le dossier une fois, l'app
 * garde ensuite le droit d'y lire (Import) et d'y écrire (Export).
 */
class Dossier(private val resolver: ContentResolver, val arbre: Uri, val docId: String) {

    class Fichier(val id: String, val nom: String, val mime: String, val uri: Uri) {
        val estDossier: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(arbre, docId)

    fun contenu(): List<Fichier> {
        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(arbre, docId)
        val colonnes = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<Fichier>()
        resolver.query(enfants, colonnes, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                result.add(Fichier(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), DocumentsContract.buildDocumentUriUsingTree(arbre, id)))
            }
        }
        return result
    }

    fun fichier(nom: String): Fichier? = contenu().firstOrNull { it.nom.equals(nom, ignoreCase = true) }

    /** Sous-dossier [nom] (créé si absent et si [creer]). */
    fun sousDossier(nom: String, creer: Boolean): Dossier? {
        contenu().firstOrNull { it.estDossier && it.nom.equals(nom, ignoreCase = true) }?.let {
            return Dossier(resolver, arbre, it.id)
        }
        if (!creer) return null
        val cree = DocumentsContract.createDocument(resolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, nom)
            ?: return null
        return Dossier(resolver, arbre, DocumentsContract.getDocumentId(cree))
    }

    fun copierVers(f: Fichier, destination: File) {
        val entree = resolver.openInputStream(f.uri) ?: throw FileNotFoundException(f.nom)
        entree.use { i -> destination.outputStream().use { o -> i.copyTo(o) } }
    }

    /** Écrit [source] sous le nom [nom] (remplace le fichier s'il existe déjà). */
    fun ecrire(nom: String, mime: String, source: File) {
        val cible = fichier(nom)?.uri
            ?: DocumentsContract.createDocument(resolver, uri, mime, nom)
            ?: throw FileNotFoundException("Impossible de créer $nom")
        val sortie = resolver.openOutputStream(cible, "wt") ?: throw FileNotFoundException(nom)
        sortie.use { o -> source.inputStream().use { i -> i.copyTo(o) } }
    }

    companion object {
        fun racine(resolver: ContentResolver, arbre: Uri): Dossier =
            Dossier(resolver, arbre, DocumentsContract.getTreeDocumentId(arbre))

        /** Nom lisible d'un dossier choisi (ex. "primary:Documents/PI_Robinetterie" -> "Documents/PI_Robinetterie"). */
        fun libelle(arbre: Uri): String = DocumentsContract.getTreeDocumentId(arbre).substringAfter(':')
    }
}
