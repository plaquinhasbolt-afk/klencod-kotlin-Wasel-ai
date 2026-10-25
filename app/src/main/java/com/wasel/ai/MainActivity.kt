package com.wasel.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لوحة رسم بسيطة — قالب KlencodIDE (Kotlin)
 *
 * الميزات:
 *   - رسم بالإصبع (Canvas + Path)
 *   - 8 ألوان
 *   - 3 سماكات فرشاة
 *   - تراجع (Undo)
 *   - مسح الكل
 *   - حفظ PNG في Pictures/KlencodDrawings (Android 10+)
 *
 * © 2026 KlencodIDE
 */
class MainActivity : Activity() {

    // ============================================================
    // الألوان الأساسية
    // ============================================================
    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()

    // ============================================================
    // 8 ألوان للرسم
    // ============================================================
    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), // أبيض
        0xFF000000.toInt(), // أسود
        0xFFFF7B72.toInt(), // أحمر
        0xFF7EE787.toInt(), // أخضر
        0xFF58A6FF.toInt(), // أزرق
        0xFFD2A8FF.toInt(), // بنفسجي
        0xFFFFD33D.toInt(), // أصفر
        0xFFFF9E64.toInt()  // برتقالي
    )

    // ============================================================
    // 3 سماكات
    // ============================================================
    private val brushSizes = floatArrayOf(4f, 10f, 22f)

    // ============================================================
    // الحالة
    // ============================================================
    private lateinit var drawingView: DrawingView
    private var currentColorIndex = 2  // أحمر افتراضي
    private var currentSizeIndex = 1   // متوسط افتراضي
    private val colorButtons = mutableListOf<Button>()
    private val sizeButtons = mutableListOf<Button>()

    // ============================================================
    // dp helper
    // ============================================================
    private fun dp(value: Int): Int =
        Math.round(value * resources.displayMetrics.density)

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        window.statusBarColor = colorBg
        window.navigationBarColor = colorBg

        // Root
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        // ============================================================
        // الترويسة العلوية
        // ============================================================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(colorSurface)
        }

        val title = TextView(this).apply {
            text = "🎨 لوحة الرسم"
            setTextColor(colorText)
            setTextSize(17f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val undoBtn = makeTopButton("↶ تراجع").apply {
            setOnClickListener { drawingView.undo() }
        }
        header.addView(undoBtn, LinearLayout.LayoutParams(-2, dp(40)))

        val clearBtn = makeTopButton("🗑 مسح").apply {
            setOnClickListener { confirmClear() }
        }
        val clearParams = LinearLayout.LayoutParams(-2, dp(40)).apply {
            leftMargin = dp(6)
        }
        header.addView(clearBtn, clearParams)

        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // منطقة الرسم
        // ============================================================
        val drawContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        drawingView = DrawingView(this)
        drawContainer.addView(
            drawingView,
            FrameLayout.LayoutParams(-1, -1)
        )

        val drawParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(drawContainer, drawParams)

        // ============================================================
        // شريط التحكم السفلي
        // ============================================================
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(colorSurface)
        }

        // --- صف الألوان ---
        val colorLabel = TextView(this).apply {
            text = "اللون:"
            setTextColor(colorMuted)
            setTextSize(12f)
        }
        controls.addView(colorLabel)

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val colorRowParams = LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(6)
        }
        controls.addView(colorRow, colorRowParams)

        palette.forEachIndexed { index, color ->
            val colorBtn = Button(this).apply {
                background = makeCircleDrawable(color)
                setPadding(0, 0, 0, 0)
                setOnClickListener { selectColor(index) }
            }
            val cp = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            colorRow.addView(colorBtn, cp)
            colorButtons.add(colorBtn)
        }

        // --- صف السماكات ---
        val sizeLabel = TextView(this).apply {
            text = "سماكة الفرشاة:"
            setTextColor(colorMuted)
            setTextSize(12f)
        }
        val sizeLabelParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        }
        controls.addView(sizeLabel, sizeLabelParams)

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeRowParams = LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(6)
        }
        controls.addView(sizeRow, sizeRowParams)

        val sizeNames = arrayOf("رفيع", "متوسط", "عريض")
        sizeNames.forEachIndexed { index, name ->
            val sizeBtn = Button(this).apply {
                text = name
                setTextSize(12f)
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                setOnClickListener { selectSize(index) }
            }
            val sp = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            sizeRow.addView(sizeBtn, sp)
            sizeButtons.add(sizeBtn)
        }

        // --- زر الحفظ ---
        val saveBtn = Button(this).apply {
            text = "💾 حفظ الصورة"
            setTextSize(14f)
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(0xFF238636.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF56D364.toInt())
            }
            elevation = dp(2).toFloat()
            setOnClickListener { saveImage() }
        }
        val saveParams = LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(14)
        }
        controls.addView(saveBtn, saveParams)

        root.addView(controls, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)

        // الاختيارات الافتراضية
        selectColor(currentColorIndex)
        selectSize(currentSizeIndex)
    }

    // ============================================================
    // دوال مساعدة للواجهة
    // ============================================================
    private fun makeTopButton(text: String): Button =
        Button(this).apply {
            this.text = text
            setTextSize(12f)
            isAllCaps = false
            setTextColor(colorText)
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply {
                setColor(colorSurfaceAlt)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), colorBorder)
            }
        }

    private fun makeCircleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), colorBorder)
        }

    private fun makeSelectedCircleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(3), colorPrimaryLight)
        }

    private fun makeSizeButtonDrawable(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (selected) colorPrimary else colorSurfaceAlt)
            setStroke(dp(1), if (selected) colorPrimaryLight else colorBorder)
        }

    private fun selectColor(index: Int) {
        currentColorIndex = index
        drawingView.setColor(palette[index])

        colorButtons.forEachIndexed { i, button ->
            button.background = if (i == index) {
                makeSelectedCircleDrawable(palette[i])
            } else {
                makeCircleDrawable(palette[i])
            }
        }
    }

    private fun selectSize(index: Int) {
        currentSizeIndex = index
        drawingView.setStrokeWidth(brushSizes[index])

        sizeButtons.forEachIndexed { i, button ->
            button.background = makeSizeButtonDrawable(i == index)
            button.setTextColor(if (i == index) Color.WHITE else colorText)
        }
    }

    // ============================================================
    // حفظ الصورة
    // ============================================================
    private fun saveImage() {
        try {
            val bitmap = drawingView.exportBitmap()
            if (bitmap == null) {
                Toast.makeText(this, "لا يوجد شيء لحفظه", Toast.LENGTH_SHORT).show()
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date())
            val fileName = "drawing_".plus(timestamp).plus(".png")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/KlencodDrawings"
                    )
                }

                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri == null) {
                    Toast.makeText(this, "تعذر إنشاء ملف", Toast.LENGTH_SHORT).show()
                    return
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                Toast.makeText(
                    this,
                    "✅ تم حفظ الصورة في:\nPictures/KlencodDrawings/".plus(fileName),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "يحتاج Android 10+ للحفظ",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ".plus(e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("مسح اللوحة")
            .setMessage("هل أنت متأكد من مسح كل الرسم؟")
            .setPositiveButton("مسح") { _, _ -> drawingView.clear() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ============================================================
    // DrawingView — Canvas مخصص
    // ============================================================
    private class DrawingView(context: Activity) : View(context) {

        private val strokes = mutableListOf<Stroke>()
        private var currentStroke: Stroke? = null

        private var currentColor = Color.RED
        private var currentStrokeWidth = 10f

        init {
            isFocusable = true
            setBackgroundColor(Color.WHITE)
        }

        fun setColor(color: Int) {
            currentColor = color
        }

        fun setStrokeWidth(width: Float) {
            currentStrokeWidth = width
        }

        fun clear() {
            strokes.clear()
            currentStroke = null
            invalidate()
        }

        fun undo() {
            if (strokes.isEmpty()) return
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }

        fun exportBitmap(): Bitmap? {
            if (width <= 0 || height <= 0) return null
            val bmp = Bitmap.createBitmap(
                width, height, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            strokes.forEach { it.draw(canvas) }
            currentStroke?.draw(canvas)
            return bmp
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            strokes.forEach { it.draw(canvas) }
            currentStroke?.draw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentStroke = Stroke(currentColor, currentStrokeWidth)
                    currentStroke?.addPoint(x, y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentStroke?.addPoint(x, y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    currentStroke?.let {
                        strokes.add(it)
                        currentStroke = null
                        invalidate()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // ============================================================
    // Stroke — ضربة واحدة (مسار واحد)
    // ============================================================
    private class Stroke(color: Int, strokeWidth: Float) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isDither = true
        }

        private val path = Path()

        fun addPoint(x: Float, y: Float) {
            if (path.isEmpty) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }
    }
}