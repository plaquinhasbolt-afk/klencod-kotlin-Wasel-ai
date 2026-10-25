package com.wasel.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wasel AI — المرحلة الأولى: دفتر ملاحظات محلي.
 *
 * الميزات:
 * - إنشاء الملاحظات وتعديلها وحذفها.
 * - حفظ الملاحظات على الجهاز باستخدام SharedPreferences.
 * - البحث في العناوين والمحتوى.
 * - واجهة عربية RTL.
 * - مساعد مبدئي يوضح أن الاتصال بنموذج AI يحتاج إلى API وخادم.
 *
 * لا يحتاج هذا الملف إلى مكتبات خارجية.
 */
class MainActivity : Activity() {

    private val bg = Color.rgb(12, 18, 30)
    private val surface = Color.rgb(22, 31, 48)
    private val surface2 = Color.rgb(30, 42, 62)
    private val primary = Color.rgb(77, 137, 255)
    private val textColor = Color.rgb(240, 244, 250)
    private val muted = Color.rgb(160, 174, 196)
    private val border = Color.rgb(48, 65, 91)

    private val prefs by lazy { getSharedPreferences("wasel_notes_store", Context.MODE_PRIVATE) }
    private val notes = mutableListOf<Note>()
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var emptyLabel: TextView

    data class Note(
        val id: Long,
        var title: String,
        var body: String,
        var updatedAt: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        loadNotes()
        showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun text(
        value: String,
        size: Float,
        color: Int = textColor,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun button(
        label: String,
        filled: Boolean = false,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(textColor)
        background = rounded(if (filled) primary else surface2, 14, if (filled) null else border)
        setPadding(dp(12), 0, dp(12), 0)
        minHeight = dp(46)
        setOnClickListener { onClick() }
    }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("Wasel AI", 25f, textColor, true))
        heading.addView(text("دفترك الدراسي الذكي", 13f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("＋ ملاحظة", true) { openEditor(null) })
        root.addView(header)

        val aiButton = button("✦ مساعد Wasel AI", false) { showAiInfo() }
        val aiParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(16)
        }
        root.addView(aiButton, aiParams)

        searchInput = EditText(this).apply {
            hint = "ابحث في العناوين والملاحظات..."
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 15f
            singleLine = true
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(surface, 14, border)
        }
        val searchParams = LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(14)
        }
        root.addView(searchInput, searchParams)

        root.addView(text("ملاحظاتي", 19f, textColor, true).apply {
            val p = LinearLayout.LayoutParams(-1, dp(42))
            p.topMargin = dp(8)
            layoutParams = p
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        emptyLabel = text("لا توجد ملاحظات بعد.\nاضغط «＋ ملاحظة» لإنشاء أول ملاحظة.", 15f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(30), dp(18), dp(30))
        }
        listContainer.addView(emptyLabel, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = text("المرحلة الأولى • حفظ محلي على هذا الجهاز", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(footer)

        setContentView(root)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderNotes(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderNotes("")
    }

    private fun renderNotes(query: String) {
        listContainer.removeAllViews()
        val filtered = notes
            .filter {
                it.title.contains(query, true) || it.body.contains(query, true)
            }
            .sortedByDescending { it.updatedAt }

        if (filtered.isEmpty()) {
            val message = if (query.isBlank()) {
                "لا توجد ملاحظات بعد.\nاضغط «＋ ملاحظة» لإنشاء أول ملاحظة."
            } else {
                "لا توجد نتائج مطابقة للبحث."
            }
            listContainer.addView(text(message, 15f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(30), dp(18), dp(30))
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }

        filtered.forEach { note ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(12), dp(15), dp(12))
                background = rounded(surface, 16, border)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                isClickable = true
                isFocusable = true
                setOnClickListener { openEditor(note.id) }
            }
            val title = note.title.ifBlank { "ملاحظة بدون عنوان" }
            card.addView(text(title, 17f, textColor, true))
            val preview = note.body.trim().replace('\n', ' ')
                .let { if (it.length > 150) it.take(150) + "…" else it }
                .ifBlank { "اضغط لكتابة محتوى الملاحظة" }
            card.addView(text(preview, 13f, muted).apply {
                setPadding(0, dp(5), 0, dp(7))
            })
            card.addView(text(formatDate(note.updatedAt), 11f, muted))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.LEFT
            }
            actions.addView(button("تعديل") { openEditor(note.id) })
            val deleteParams = LinearLayout.LayoutParams(-2, dp(46)).apply {
                leftMargin = dp(8)
            }
            actions.addView(button("حذف") { confirmDelete(note.id) }, deleteParams)
            val actionParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            }
            card.addView(actions, actionParams)
            val cardParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
            listContainer.addView(card, cardParams)
        }
    }

    private fun openEditor(noteId: Long?) {
        val existing = noteId?.let { id -> notes.firstOrNull { it.id == id } }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val titleInput = EditText(this).apply {
            hint = "عنوان الملاحظة"
            setText(existing?.title.orEmpty())
            setTextColor(textColor)
            setHintTextColor(muted)
            textSize = 18f
            singleLine = true
            gravity = Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(surface2, 12, border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val bodyInput = EditText(this).apply {
            hint = "اكتب ملاحظاتك هنا..."
            setText(existing?.body.orEmpty())
            setTextColor(textColor)
            setHintTextColor(muted)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minLines = 8
            maxLines = 14
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(surface2, 12, border)
        }
        panel.addView(titleInput, LinearLayout.LayoutParams(-1, dp(54)))
        panel.addView(bodyInput, LinearLayout.LayoutParams(-1, dp(220)).apply {
            topMargin = dp(12)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "ملاحظة جديدة" else "تعديل الملاحظة")
            .setView(panel)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                if (title.isBlank() && body.isBlank()) {
                    Toast.makeText(this, "اكتب عنوانًا أو محتوى قبل الحفظ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                if (existing == null) {
                    notes.add(Note(now, title, body, now))
                } else {
                    existing.title = title
                    existing.body = body
                    existing.updatedAt = now
                }
                saveNotes()
                dialog.dismiss()
                renderNotes(searchInput.text.toString())
                Toast.makeText(this, "تم حفظ الملاحظة على الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(noteId: Long) {
        AlertDialog.Builder(this)
            .setTitle("حذف الملاحظة")
            .setMessage("هل تريد حذف هذه الملاحظة نهائيًا من هذا الجهاز؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                notes.removeAll { it.id == noteId }
                saveNotes()
                renderNotes(searchInput.text.toString())
                Toast.makeText(this, "تم حذف الملاحظة", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAiInfo() {
        AlertDialog.Builder(this)
            .setTitle("✦ مساعد Wasel AI")
            .setMessage(
                "واجهة المساعد جاهزة كبداية، لكن الذكاء الاصطناعي الحقيقي يحتاج إلى ربط آمن بخدمة AI عبر خادم.\\n\\n" +
                "عند إضافة الاتصال يمكن تفعيل تلخيص الملاحظات، وشرح الدروس، وإنشاء أسئلة للمراجعة. " +
                "لا تضع مفتاح API سريًا داخل تطبيق Android."
            )
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun loadNotes() {
        notes.clear()
        val raw = prefs.getString("notes_json", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                notes.add(
                    Note(
                        id = item.optLong("id", System.currentTimeMillis() + i),
                        title = item.optString("title", ""),
                        body = item.optString("body", ""),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            notes.clear()
            Toast.makeText(this, "تعذر قراءة الملاحظات المحفوظة", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveNotes() {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("body", note.body)
                put("updatedAt", note.updatedAt)
            })
        }
        prefs.edit().putString("notes_json", array.toString()).apply()
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date(timestamp))
        } catch (_: Exception) {
            ""
        }
    }
}