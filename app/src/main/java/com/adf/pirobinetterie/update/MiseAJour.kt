package com.adf.pirobinetterie.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.adf.pirobinetterie.BuildConfig
import com.adf.pirobinetterie.data.FichiersProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise à jour de l'app depuis la release GitHub à tag fixe "tablette-latest" (republiée à chaque
 * lancement manuel du workflow "Build APK"). Même principe que PV Jointage : toucher le logo
 * ADF de l'écran principal. Aucune connexion à un compte n'est nécessaire (dépôt public).
 */
object MiseAJour {

    /** Release GitHub suivie : "tablette-beta" pour la version BETA, "tablette-latest" pour la finale. */
    private const val API_URL =
        "https://api.github.com/repos/dabom1993-pixel/Pr-paration-Int-gr-e/releases/tags/" + BuildConfig.CANAL_MAJ
    private const val NOM_APK = BuildConfig.NOM_APK

    private const val PREFS = "maj_prefs"
    private const val DERNIERE = "dernier_asset"
    private const val EN_ATTENTE = "asset_en_attente"

    /** [version] : numéro publié dans version.txt (null pour une release plus ancienne). */
    class Info(val id: Long, val url: String, val taille: Long, val version: Int? = null)

    fun reseauDisponible(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** À appeler en arrière-plan. null si aucune version publiée ou pas de connexion. */
    fun derniereVersion(): Info? {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PIRobinetterie-Android")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            var apk: JSONObject? = null
            var urlVersion: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                when (a.optString("name")) {
                    NOM_APK -> apk = a
                    "version.txt" -> urlVersion = a.getString("browser_download_url")
                }
            }
            val a = apk ?: return null
            return Info(
                a.getLong("id"), a.getString("browser_download_url"), a.optLong("size", -1L),
                urlVersion?.let { lireVersion(it) }
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun lireVersion(url: String): Int? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
        }
        try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
            } else null
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    /**
     * true si [info] diffère de la dernière version installée par ce biais. Au tout premier
     * contrôle, la version publiée est considérée comme celle en cours d'utilisation.
     */
    fun estNouvelle(ctx: Context, info: Info): Boolean {
        // Référence commune avec ADF TAR : le numéro de version de l'APK installé. Une mise à
        // jour faite depuis ADF TAR est ainsi reconnue ici, et inversement.
        info.version?.let { return it > BuildConfig.VERSION_CODE }
        // Ancienne release sans version.txt : suivi par identifiant de fichier.
        val p = prefs(ctx)
        val derniere = p.getLong(DERNIERE, -1L)
        if (derniere == -1L) {
            p.edit().putLong(DERNIERE, info.id).apply()
            return false
        }
        return derniere != info.id
    }

    /** À appeler en arrière-plan. [progression] reçoit 0..100. */
    fun telecharger(ctx: Context, info: Info, progression: (Int) -> Unit): File {
        val dest = File(File(ctx.filesDir, "maj").apply { mkdirs() }, NOM_APK)
        if (dest.exists()) dest.delete()
        val conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val total = info.taille.takeIf { it > 0 } ?: conn.contentLengthLong
            conn.inputStream.use { entree ->
                FileOutputStream(dest).use { sortie ->
                    val tampon = ByteArray(8 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        if (total > 0) progression(((lu * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return dest
    }

    fun marquerEnAttente(ctx: Context, info: Info) {
        prefs(ctx).edit().putLong(EN_ATTENTE, info.id).apply()
    }

    fun intentInstallation(ctx: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FichiersProvider.uri(ctx, apk), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    internal fun installee(ctx: Context) {
        val p = prefs(ctx)
        val attente = p.getLong(EN_ATTENTE, -1L)
        if (attente != -1L) p.edit().putLong(DERNIERE, attente).remove(EN_ATTENTE).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Reçu juste après le remplacement de l'app par une mise à jour : mémorise la version installée. */
class MiseAJourInstallee : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) MiseAJour.installee(context)
    }
}
