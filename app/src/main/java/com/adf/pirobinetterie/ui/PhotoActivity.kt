@file:Suppress("DEPRECATION")

package com.adf.pirobinetterie.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.adf.pirobinetterie.pdf.Images
import java.io.File

/**
 * Prise de photo intégrée à l'application (au lieu de l'appareil photo de la tablette), avec un
 * bouton "Retour" toujours visible pour sortir sans photo. Après la prise : "Garder" ou "Reprendre".
 * Résultat : RESULT_OK si la photo a été enregistrée dans le fichier [EXTRA_FICHIER].
 *
 * Utilise l'API android.hardware.Camera : ancienne mais disponible sur toutes les tablettes
 * Android 7 à 14, et bien plus simple que Camera2 pour une simple photo.
 */
class PhotoActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var fichier: File
    private var camera: Camera? = null
    private var infoCamera = Camera.CameraInfo()
    private var surfacePrete = false
    private var orientationAppareil = 0
    private var ecouteurOrientation: OrientationEventListener? = null

    private lateinit var cadreApercu: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var controle: ImageView
    private lateinit var boutonsPrise: LinearLayout
    private lateinit var boutonsControle: LinearLayout
    private lateinit var declencheur: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fichier = File(intent.getStringExtra(EXTRA_FICHIER) ?: run { finish(); return })
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(construire())
        ecouteurOrientation = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o != ORIENTATION_UNKNOWN) orientationAppareil = o
            }
        }
    }

    private fun construire(): View {
        val racine = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        cadreApercu = FrameLayout(this)
        surface = SurfaceView(this).apply { holder.addCallback(this@PhotoActivity) }
        cadreApercu.addView(surface, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        racine.addView(cadreApercu, FrameLayout.LayoutParams(MATCH, MATCH))

        controle = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        racine.addView(controle, FrameLayout.LayoutParams(MATCH, MATCH))

        // Bouton Retour : toujours en haut à gauche.
        racine.addView(bouton("✕  Retour", Couleurs.ROUGE) { annuler() }.apply { textSize = 18f },
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(16), dp(16), 0, 0) })

        declencheur = bouton("📷  Prendre la photo", Couleurs.VERT) { declencher() }.apply {
            textSize = 20f
            minHeight = dp(72)
        }
        boutonsPrise = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(declencheur, lp(WRAP, WRAP))
        }
        racine.addView(boutonsPrise, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(24)) })

        boutonsControle = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(bouton("↺  Reprendre", Couleurs.MARINE_CLAIR) { reprendre() }.apply { textSize = 19f; minHeight = dp(64) },
                lp(WRAP, WRAP).marges(0, 0, dp(24), 0))
            addView(bouton("✔  Garder cette photo", Couleurs.VERT) { garder() }.apply { textSize = 19f; minHeight = dp(64) })
        }
        racine.addView(boutonsControle, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(24)) })
        return racine
    }

    override fun onResume() {
        super.onResume()
        ecouteurOrientation?.enable()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_PERMISSION)
            return
        }
        ouvrirCamera()
    }

    override fun onPause() {
        super.onPause()
        ecouteurOrientation?.disable()
        fermerCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQ_PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) ouvrirCamera()
        else {
            toast("Autorisation de l'appareil photo refusée")
            annuler()
        }
    }

    @Deprecated("API framework")
    override fun onBackPressed() = annuler()

    // --- Caméra ---------------------------------------------------------------------------

    private fun ouvrirCamera() {
        if (camera != null) return
        val id = (0 until Camera.getNumberOfCameras()).firstOrNull { i ->
            Camera.CameraInfo().also { Camera.getCameraInfo(i, it) }.facing == Camera.CameraInfo.CAMERA_FACING_BACK
        } ?: 0
        camera = try {
            Camera.open(id)
        } catch (e: Exception) {
            toast("Appareil photo indisponible : ${e.message}")
            annuler()
            return
        }
        Camera.getCameraInfo(id, infoCamera)
        val cam = camera ?: return
        val p = cam.parameters
        // Photo : plus grande taille disponible ; aperçu : taille la plus proche du même format.
        val photo = p.supportedPictureSizes.maxByOrNull { it.width.toLong() * it.height }
        if (photo != null) p.setPictureSize(photo.width, photo.height)
        val ratio = photo?.let { it.width.toDouble() / it.height } ?: (4.0 / 3)
        val apercu = p.supportedPreviewSizes
            .sortedWith(compareBy({ Math.abs(it.width.toDouble() / it.height - ratio) }, { -it.width.toLong() * it.height }))
            .firstOrNull()
        if (apercu != null) p.setPreviewSize(apercu.width, apercu.height)
        if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in p.supportedFocusModes) p.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        else if (Camera.Parameters.FOCUS_MODE_AUTO in p.supportedFocusModes) p.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        p.jpegQuality = 92
        try {
            cam.parameters = p
        } catch (_: Exception) {
        }
        val rotationAffichage = rotationAffichage()
        cam.setDisplayOrientation(rotationAffichage)
        apercu?.let { ajusterApercu(it.width, it.height, rotationAffichage) }
        if (surfacePrete) demarrerApercu()
    }

    private fun demarrerApercu() {
        val cam = camera ?: return
        try {
            cam.setPreviewDisplay(surface.holder)
            cam.startPreview()
        } catch (e: Exception) {
            toast("Aperçu impossible : ${e.message}")
        }
    }

    private fun fermerCamera() {
        camera?.let {
            try {
                it.stopPreview()
            } catch (_: Exception) {
            }
            it.release()
        }
        camera = null
    }

    private fun rotationAffichage(): Int {
        val degres = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (infoCamera.orientation - degres + 360) % 360
    }

    /** Aperçu au bon format (pas déformé), centré dans l'écran. */
    private fun ajusterApercu(w: Int, h: Int, rotation: Int) {
        cadreApercu.post {
            val (pw, ph) = if (rotation % 180 == 0) w to h else h to w
            val cw = cadreApercu.width.takeIf { it > 0 } ?: return@post
            val ch = cadreApercu.height.takeIf { it > 0 } ?: return@post
            val echelle = minOf(cw.toFloat() / pw, ch.toFloat() / ph)
            surface.layoutParams = FrameLayout.LayoutParams((pw * echelle).toInt(), (ph * echelle).toInt(), Gravity.CENTER)
        }
    }

    private fun declencher() {
        val cam = camera ?: return
        declencheur.isEnabled = false
        // Orientation de la photo selon la position réelle de la tablette.
        try {
            val o = (orientationAppareil + 45) / 90 * 90 % 360
            val p = cam.parameters
            p.setRotation((infoCamera.orientation + o) % 360)
            cam.parameters = p
        } catch (_: Exception) {
        }
        val prendre = {
            try {
                cam.takePicture(null, null) { donnees, _ ->
                    try {
                        fichier.parentFile?.mkdirs()
                        fichier.writeBytes(donnees)
                        afficherControle()
                    } catch (e: Exception) {
                        toast("Enregistrement impossible : ${e.message}")
                        declencheur.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                toast("Photo impossible : ${e.message}")
                declencheur.isEnabled = true
            }
        }
        if (cam.parameters.focusMode == Camera.Parameters.FOCUS_MODE_AUTO) {
            try {
                cam.autoFocus { _, _ -> prendre() }
            } catch (_: Exception) {
                prendre()
            }
        } else prendre()
    }

    private fun afficherControle() {
        val bmp = Images.charger(fichier.absolutePath, 1600)
        controle.setImageBitmap(bmp)
        controle.visibility = View.VISIBLE
        boutonsPrise.visibility = View.GONE
        boutonsControle.visibility = View.VISIBLE
    }

    private fun reprendre() {
        fichier.delete()
        controle.setImageDrawable(null)
        controle.visibility = View.GONE
        boutonsControle.visibility = View.GONE
        boutonsPrise.visibility = View.VISIBLE
        declencheur.isEnabled = true
        try {
            camera?.startPreview()
        } catch (_: Exception) {
        }
    }

    private fun garder() {
        setResult(RESULT_OK)
        finish()
    }

    private fun annuler() {
        if (::fichier.isInitialized) fichier.delete()
        setResult(RESULT_CANCELED)
        finish()
    }

    // --- SurfaceHolder.Callback -----------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfacePrete = true
        demarrerApercu()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfacePrete = false
    }

    companion object {
        const val EXTRA_FICHIER = "fichier"
        private const val REQ_PERMISSION = 20
    }
}
