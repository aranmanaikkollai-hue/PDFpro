package com.propdf.editor.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        scroll.addView(root)

        // Header
        root.addView(TextView(this).apply {
            text = "⚙️ Settings"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(24))
        })

        val prefs = getSharedPreferences("propdf_prefs", Context.MODE_PRIVATE)

        // Dark Mode Toggle
        root.addView(createSettingCard("🌙 Dark Mode", "Enable dark theme throughout the app") {
            val switch = Switch(this).apply {
                isChecked = prefs.getBoolean("dark_mode", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()
                    Toast.makeText(this@SettingsActivity, "Restart app to apply", Toast.LENGTH_SHORT).show()
                }
            }
            it.addView(switch)
        })

        // Auto-save Annotations
        root.addView(createSettingCard("💾 Auto-save Annotations", "Automatically save annotations when exiting viewer") {
            val switch = Switch(this).apply {
                isChecked = prefs.getBoolean("auto_save", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("auto_save", isChecked).apply()
                }
            }
            it.addView(switch)
        })

        // Default Annotation Color
        root.addView(createSettingCard("🎨 Default Pen Color", "Choose your preferred annotation color") {
            val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.BLACK)
            val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            colors.forEach { color ->
                val colorView = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) }
                    background = GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(20).toFloat()
                        setStroke(dp(2), Color.WHITE)
                    }
                    isClickable = true
                    setOnClickListener {
                        prefs.edit().putInt("default_color", color).apply()
                        Toast.makeText(this@SettingsActivity, "Color saved", Toast.LENGTH_SHORT).show()
                    }
                }
                grid.addView(colorView)
            }
            it.addView(grid)
        })

        // Clear Cache
        root.addView(createSettingCard("🗑️ Clear Cache", "Free up storage space") {
            val btn = Button(this).apply {
                text = "Clear Now"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E74C3C"))
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener {
                    cacheDir.deleteRecursively()
                    Toast.makeText(this@SettingsActivity, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
            }
            it.addView(btn)
        })

        // About
        root.addView(createSettingCard("ℹ️ About ProPDF", "Version 1.0.0 • Built with ❤️") {
            val tv = TextView(this).apply {
                text = "ProPDF Editor is a professional PDF editing tool with advanced annotations, conversion tools, and document management features."
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
            }
            it.addView(tv)
        })

        setContentView(scroll)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun createSettingCard(title: String, subtitle: String, content: (LinearLayout) -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(12).toFloat()
            }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(4), 0, dp(12))
        })
        content(card)
        return card
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
