package com.adf.pirobinetterie.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.adf.pirobinetterie.data.PlotPlan
import com.adf.pirobinetterie.excel.PlotPlanImporter

/**
 * Plan affiché en grand : zoom et déplacement à deux doigts (double-tap = vue entière).
 * En mode [edition], le point se déplace en le faisant glisser, ou en touchant le plan à
 * l'endroit voulu. Le point est dessiné comme dans l'image de la fiche ([PlotPlan.rectangle]).
 */
class PlanView(context: Context) : View(context) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            ajuste = false
            invalidate()
        }

    /** Points dessinés par-dessus le plan (fractions de la largeur / hauteur du plan). */
    var points: List<PlotPlanImporter.Point> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var edition = false

    /** Appelé quand l'utilisateur déplace le point (nouvelles fractions fx, fy). */
    var surDeplacement: ((Double, Double) -> Unit)? = null

    private val matrice = Matrix()
    private val inverse = Matrix()
    private var echelleMin = 1f
    private var ajuste = false
    private var glissePoint = false
    private val peinture = Paint(Paint.FILTER_BITMAP_FLAG)

    private val zoom = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val actuelle = echelle()
            val facteur = (actuelle * d.scaleFactor).coerceIn(echelleMin, echelleMin * 12f) / actuelle
            matrice.postScale(facteur, facteur, d.focusX, d.focusY)
            borner()
            invalidate()
            return true
        }
    })

    private val gestes = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            glissePoint = edition && touchePoint(e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoom.isInProgress) return false
            if (glissePoint) deplacerVers(e2.x, e2.y)
            else {
                matrice.postTranslate(-dx, -dy)
                borner()
                invalidate()
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (edition) deplacerVers(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            ajuster()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoom.onTouchEvent(event)
        if (event.pointerCount == 1 || !zoom.isInProgress) gestes.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) glissePoint = false
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ajuste = false
    }

    /** Vue entière du plan, centrée. */
    fun ajuster() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        echelleMin = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        matrice.reset()
        matrice.postScale(echelleMin, echelleMin)
        matrice.postTranslate((width - b.width * echelleMin) / 2, (height - b.height * echelleMin) / 2)
        ajuste = true
        invalidate()
    }

    /** Centre la vue sur le point, avec un zoom confortable. */
    fun centrerSurPoint() {
        val b = bitmap ?: return
        val p = points.firstOrNull() ?: return
        if (!ajuste) ajuster()
        val e = echelleMin * 3f
        matrice.reset()
        matrice.postScale(e, e)
        matrice.postTranslate(width / 2f - (p.fx * b.width).toFloat() * e, height / 2f - (p.fy * b.height).toFloat() * e)
        borner()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF3A3F47.toInt())
        val b = bitmap ?: return
        if (!ajuste) ajuster()
        canvas.save()
        canvas.concat(matrice)
        canvas.drawBitmap(b, 0f, 0f, peinture)
        // Contour d'au moins 2 pixels à l'écran quel que soit le zoom.
        PlotPlan.dessinerPoints(canvas, points, b.width, b.height, maxOf(2f / echelle(), b.width / 500f))
        if (edition) {
            val cadre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.RED
                strokeWidth = 2f / echelle()
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f / echelle(), 6f / echelle()), 0f)
            }
            points.firstOrNull()?.let { p ->
                val r = PlotPlan.rectangle(p, b.width, b.height)
                val m = 6f / echelle()
                canvas.drawRect(r.left - m, r.top - m, r.right + m, r.bottom + m, cadre)
            }
        }
        canvas.restore()
    }

    private fun echelle(): Float {
        val v = FloatArray(9)
        matrice.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    /** Empêche de faire sortir le plan de l'écran. */
    private fun borner() {
        val b = bitmap ?: return
        val r = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        matrice.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2 - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2 - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        matrice.postTranslate(dx, dy)
    }

    private fun versImage(x: Float, y: Float): FloatArray {
        matrice.invert(inverse)
        val pt = floatArrayOf(x, y)
        inverse.mapPoints(pt)
        return pt
    }

    private fun touchePoint(x: Float, y: Float): Boolean {
        val b = bitmap ?: return false
        val p = points.firstOrNull() ?: return false
        val r = PlotPlan.rectangle(p, b.width, b.height)
        val m = 40f / echelle() // marge tactile ~40 px à l'écran
        val pt = versImage(x, y)
        return pt[0] in (r.left - m)..(r.right + m) && pt[1] in (r.top - m)..(r.bottom + m)
    }

    private fun deplacerVers(x: Float, y: Float) {
        val b = bitmap ?: return
        val pt = versImage(x, y)
        val fx = (pt[0] / b.width).toDouble().coerceIn(0.0, 1.0)
        val fy = (pt[1] / b.height).toDouble().coerceIn(0.0, 1.0)
        surDeplacement?.invoke(fx, fy)
    }
}
