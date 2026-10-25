
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
import android.view.inputmethod.InputType
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

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 18, 30)
    private val surface = Color.rgb(22, 31, 48)
    private val surface2 = Color.rgb(30, 42, 62)
    private val primary = Color.rgb(77, 137, 255)
    private val ink = Color.rgb(240, 244, 250)
    private val muted = Color.rgb(160, 174, 196)
    private val border = Color.rgb(48, 65, 91)

    private val prefs by lazy {
        getSharedPreferences("wasel_notes_store", Context.MODE_PRIVATE)
    }

    private val notes = mutableListOf<Note>()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText

    private data class Note(
        val id: Long,
        var title: String,
        var body: String,
        var updated: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        loadNotes()
        buildHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(
        color: Int,
        radius: Int,
        stroke: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int = ink,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun action(
        value: String,
        primaryButton: Boolean = false,
        click: () -> Unit
    ): Button {
        return Button(this).apply {
            text = value
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = shape(
                if (primaryButton) primary else surface2,
                12,
                if (primaryButton) null else border
            )
            setPadding(dp(10), 0, dp(10), 0)
            minHeight = dp(44)
            setOnClickListener { click() }
        }
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titles.addView(label("Wasel AI", 25f, ink, true))
        titles.addView(label("دفترك الدراسي الذكي", 13f, muted))

        header.addView(
            titles,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(action("＋ ملاحظة", true) {
            editNote(null)
        })

        root.addView(header)

        root.addView(
            action("✦ مساعد Wasel AI") {
                showAiInfo()
            },
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(14)
            }
        )

        search = EditText(this).apply {
            hint = "ابحث في العناوين والملاحظات..."
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), 0, dp(12), 0)
            background = shape(surface, 12, border)
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(12)
            }
        )

        root.addView(
            label("ملاحظاتي", 19f, ink, true),
            LinearLayout.LayoutParams(-1, dp(42)).apply {
                topMargin = dp(6)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(
            label("المرحلة الأولى • حفظ محلي على هذا الجهاز", 11f, muted)
                .apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                }
        )

        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                render(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        render("")
    }

    private fun render(query: String) {
        list.removeAllViews()

        val filtered = notes.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.body.contains(query, ignoreCase = true)
        }.sortedByDescending { it.updated }

        if (filtered.isEmpty()) {
            val message = if (query.isBlank()) {
                "لا توجد ملاحظات بعد.\nاضغط «＋ ملاحظة» لإنشاء أول ملاحظة."
            } else {
                "لا توجد نتائج مطابقة للبحث."
            }

            list.addView(
                label(message, 15f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(28), dp(16), dp(28))
                },
                LinearLayout.LayoutParams(-1, -2)
            )
            return
        }

        filtered.forEach { note ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(surface, 14, border)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }

            card.addView(
                label(
                    note.title.ifBlank { "ملاحظة بدون عنوان" },
                    17f,
                    ink,
                    true
                )
            )

            val preview = note.body.trim()
                .replace('\n', ' ')
                .let {
                    if (it.length > 140) it.take(140) + "…" else it
                }
                .ifBlank { "اضغط تعديل لإضافة محتوى" }

            card.addView(
                label(preview, 13f, muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(5)
                }
            )

            card.addView(
                label(date(note.updated), 11f, muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(6)
                }
            )

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.LEFT
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }

            row.addView(action("تعديل") {
                editNote(note.id)
            })

            row.addView(
                action("حذف") {
                    deleteNote(note.id)
                },
                LinearLayout.LayoutParams(-2, dp(44)).apply {
                    leftMargin = dp(8)
                }
            )

            card.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )

            list.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                }
            )
        }
    }

    private fun editNote(id: Long?) {
        val old = id?.let { key ->
            notes.firstOrNull { it.id == key }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = EditText(this).apply {
            hint = "عنوان الملاحظة"
            setText(old?.title.orEmpty())
            setTextColor(ink)
            setHintTextColor(muted)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            gravity = Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = shape(surface2, 10, border)
        }

        val body = EditText(this).apply {
            hint = "اكتب ملاحظاتك هنا..."
            setText(old?.body.orEmpty())
            setTextColor(ink)
            setHintTextColor(muted)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minLines = 8
            maxLines = 14
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = shape(surface2, 10, border)
        }

        panel.addView(title, LinearLayout.LayoutParams(-1, dp(54)))

        panel.addView(
            body,
            LinearLayout.LayoutParams(-1, dp(210)).apply {
                topMargin = dp(10)
            }
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (old == null) "ملاحظة جديدة" else "تعديل الملاحظة")
            .setView(panel)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val t = title.text.toString().trim()
                    val b = body.text.toString().trim()

                    if (t.isBlank() && b.isBlank()) {
                        Toast.makeText(
                            this,
                            "اكتب عنوانًا أو محتوى قبل الحفظ",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    val now = System.currentTimeMillis()

                    if (old == null) {
                        notes.add(Note(now, t, b, now))
                    } else {
                        old.title = t
                        old.body = b
                        old.updated = now
                    }

                    saveNotes()
                    dialog.dismiss()
                    render(search.text.toString())

                    Toast.makeText(
                        this,
                        "تم حفظ الملاحظة على الجهاز",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        dialog.show()
    }

    private fun deleteNote(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("حذف الملاحظة")
            .setMessage("هل تريد حذف هذه الملاحظة من الجهاز؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                notes.removeAll { it.id == id }
                saveNotes()
                render(search.text.toString())

                Toast.makeText(
                    this,
                    "تم حذف الملاحظة",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showAiInfo() {
        AlertDialog.Builder(this)
            .setTitle("مساعد Wasel AI")
            .setMessage(
                "هذه واجهة مبدئية. تشغيل الذكاء الاصطناعي الحقيقي يحتاج إلى ربط بخدمة عبر خادم آمن.\n\n" +
                    "لا تضع مفتاح API سريًا داخل تطبيق Android."
            )
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun loadNotes() {
        notes.clear()

        try {
            val array = JSONArray(
                prefs.getString("notes_json", "[]") ?: "[]"
            )

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                notes.add(
                    Note(
                        obj.optLong("id", System.currentTimeMillis() + i),
                        obj.optString("title", ""),
                        obj.optString("body", ""),
                        obj.optLong("updated", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            notes.clear()
        }
    }

    private fun saveNotes() {
        val array = JSONArray()

        notes.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("body", note.body)
                    put("updated", note.updated)
                }
            )
        }

        prefs.edit()
            .putString("notes_json", array.toString())
            .apply()
    }

    private fun date(time: Long): String {
        return SimpleDateFormat(
            "yyyy/MM/dd  HH:mm",
            Locale.getDefault()
        ).format(Date(time))
    }
}
