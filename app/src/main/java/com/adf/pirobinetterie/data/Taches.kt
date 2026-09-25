package com.adf.pirobinetterie.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Travaux longs (import, export, PDF) exécutés hors de l'écran, un à la fois et dans l'ordre. */
object Taches {
    private val executeur = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    fun <T> lancer(travail: () -> T, ok: (T) -> Unit, erreur: (Exception) -> Unit) {
        executeur.execute {
            try {
                val r = travail()
                principal.post { ok(r) }
            } catch (e: Exception) {
                principal.post { erreur(e) }
            }
        }
    }

    fun enFond(travail: () -> Unit) = executeur.execute { runCatching(travail) }
}
