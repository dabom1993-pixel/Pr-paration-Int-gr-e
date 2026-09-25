package com.adf.pirobinetterie.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Couleurs de l'app (bleu marine et vert du logo Groupe ADF). */
object Couleurs {
    const val MARINE = 0xFF22385F.toInt()
    const val MARINE_CLAIR = 0xFF3A5A8C.toInt()
    const val VERT = 0xFF4E9A52.toInt()
    const val ORANGE = 0xFFE08A00.toInt()
    const val ROUGE = 0xFFC62828.toInt()
    const val GRIS = 0xFF8A8F98.toInt()
    const val FOND = 0xFFF4F6F8.toInt()
    const val CARTE = 0xFFFFFFFF.toInt()
    const val LIBELLE = 0xFFEEF1F6.toInt()
    const val BORDURE = 0xFFD5DAE1.toInt()
    const val TEXTE = 0xFF1E232B.toInt()
    const val JAUNE = 0xFFFFFF00.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun fondArrondi(couleur: Int, rayon: Float, bordure: Int? = null, epaisseur: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(couleur)
        cornerRadius = rayon
        if (bordure != null) setStroke(epaisseur, bordure)
    }

/** Fond qui s'assombrit quand on appuie dessus. */
fun fondCliquable(couleur: Int, appuye: Int, rayon: Float = 0f, bordure: Int? = null, epaisseur: Int = 0): StateListDrawable =
    StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), fondArrondi(appuye, rayon, bordure, epaisseur))
        addState(intArrayOf(), fondArrondi(couleur, rayon, bordure, epaisseur))
    }

fun Context.bouton(texte: String, couleur: Int = Couleurs.MARINE, action: () -> Unit): Button =
    Button(this).apply {
        text = texte
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(52)
        minimumHeight = dp(52)
        setPadding(dp(18), 0, dp(18), 0)
        background = fondCliquable(couleur, assombrir(couleur), dp(8).toFloat())
        stateListAnimator = null
        setOnClickListener { action() }
    }

fun assombrir(c: Int): Int {
    val f = 0.8f
    return Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
}

fun Context.texte(t: String, taille: Float = 16f, gras: Boolean = false, couleur: Int = Couleurs.TEXTE): TextView =
    TextView(this).apply {
        text = t
        textSize = taille
        setTextColor(couleur)
        if (gras) typeface = Typeface.DEFAULT_BOLD
    }

/** Carte blanche arrondie avec un titre. */
fun Context.carte(titre: String?): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = fondArrondi(Couleurs.CARTE, dp(10).toFloat(), Couleurs.BORDURE, dp(1))
    setPadding(dp(14), dp(12), dp(14), dp(14))
    if (titre != null) addView(texte(titre, 18f, true, Couleurs.MARINE).apply { setPadding(0, 0, 0, dp(8)) })
}

fun Context.badge(t: String, couleur: Int): TextView = texte(t, 14f, true, Color.WHITE).apply {
    background = fondArrondi(couleur, dp(14).toFloat())
    setPadding(dp(12), dp(5), dp(12), dp(5))
    gravity = Gravity.CENTER
}

fun lp(w: Int, h: Int, poids: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, poids)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun LinearLayout.LayoutParams.marges(g: Int, h: Int, d: Int, b: Int) = apply { setMargins(g, h, d, b) }

fun Activity.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

fun Activity.message(titre: String, texte: String, ok: (() -> Unit)? = null) {
    AlertDialog.Builder(this).setTitle(titre).setMessage(texte)
        .setPositiveButton("OK") { _, _ -> ok?.invoke() }
        .show()
}

fun Activity.confirmer(titre: String, texte: String, libelleOk: String, action: () -> Unit) {
    AlertDialog.Builder(this).setTitle(titre).setMessage(texte)
        .setPositiveButton(libelleOk) { _, _ -> action() }
        .setNegativeButton("Annuler", null)
        .show()
}

/** Fenêtre d'attente (travail en cours). */
fun Activity.attente(message: String): AlertDialog {
    val vue = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
        addView(ProgressBar(this@attente))
        addView(texte(message, 17f).apply { setPadding(dp(20), 0, 0, 0) })
    }
    return AlertDialog.Builder(this).setView(vue).setCancelable(false).show()
}

/**
 * Saisie d'une valeur : zone de texte + choix rapides (liste de l'onglet DATA).
 * Toucher un choix valide immédiatement.
 */
fun Activity.saisir(
    titre: String, valeur: String, options: List<String>, multiligne: Boolean = false,
    numerique: Boolean = false, valider: (String) -> Unit
) {
    val contenu = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(8))
    }
    val edition = EditText(this).apply {
        setText(valeur)
        textSize = 20f
        setSelection(text.length)
        inputType = when {
            multiligne -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            numerique -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multiligne) {
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
        } else {
            setSingleLine(true)
        }
    }
    contenu.addView(edition, lp(MATCH, WRAP))

    var dialogue: AlertDialog? = null
    if (options.isNotEmpty()) {
        contenu.addView(texte("Choix rapides :", 14f, true, Couleurs.GRIS).apply { setPadding(0, dp(12), 0, dp(4)) })
        val grille = GridLayout(this).apply { columnCount = 3 }
        for (o in options) {
            val b = bouton(o, if (o == valeur) Couleurs.VERT else Couleurs.MARINE_CLAIR) {
                dialogue?.dismiss()
                valider(o)
            }
            b.textSize = 15f
            grille.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        contenu.addView(grille, lp(MATCH, WRAP))
    }

    dialogue = AlertDialog.Builder(this)
        .setTitle(titre)
        .setView(ScrollView(this).apply { addView(contenu) })
        .setPositiveButton("OK") { _, _ -> valider(edition.text.toString().trim()) }
        .setNeutralButton("Vider") { _, _ -> valider("") }
        .setNegativeButton("Annuler", null)
        .create()
    dialogue.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialogue.show()
    edition.requestFocus()
}

/** Ligne "libellé | valeur" cliquable d'une carte. */
fun Context.ligneChamp(libelle: String, valeur: String, jaune: Boolean, clic: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        minimumHeight = dp(50)
        val l = texte(libelle, 16f, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Couleurs.LIBELLE)
        }
        val v = texte(valeur.ifEmpty { "—" }, 17f, false, if (valeur.isEmpty()) Couleurs.GRIS else Couleurs.TEXTE).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = fondCliquable(if (jaune) Couleurs.JAUNE else Couleurs.CARTE, 0xFFDDE6F3.toInt())
            setOnClickListener { clic() }
        }
        addView(l, lp(0, MATCH, 0.42f))
        addView(v, lp(0, MATCH, 0.58f))
        layoutParams = lp(MATCH, WRAP).marges(0, 0, 0, dp(2))
    }
