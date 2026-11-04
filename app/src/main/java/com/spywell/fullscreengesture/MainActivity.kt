package com.spywell.fullscreengesture

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private var updatingNav = false
    private val secure = "android.permission.WRITE_SECURE_SETTINGS"
    private val grantCmd = "pm grant com.spywell.fullscreengesture android.permission.WRITE_SECURE_SETTINGS"

    private val navStatus by lazy {
        TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
    }
    private val navSwitch by lazy {
        Switch(this).apply {
            text = "Hide phone's navigation buttons"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Full Screen Gesture"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Build v$version"
            textSize = 13f
            alpha = 0.6f
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(20), 0, dp(8))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(section("Edges"))
        root.addView(toggle("Left edge swipe → Back", Prefs.LEFT))
        root.addView(toggle("Right edge swipe → Back", Prefs.RIGHT))
        root.addView(toggle("Bottom swipe up → Home (hold → Recents)", Prefs.BOTTOM))
        root.addView(toggle("Vibrate on gesture", Prefs.HAPTIC))

        root.addView(section("Hide phone's own navigation buttons"))
        root.addView(navStatus)
        root.addView(Button(this).apply {
            text = "Open phone navigation settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        root.addView(navSwitch)
        root.addView(Button(this).apply {
            text = "Copy one-time setup command"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cmd", grantCmd))
                Toast.makeText(this@MainActivity, "Command copied", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Android does not let normal apps hide the buttons. Option 1: in your phone's own " +
                "Settings, switch to gesture / full screen navigation (usually under Display, System " +
                "or Additional settings). Option 2: run the copied command once using Wireless " +
                "debugging (Developer options) in an on-phone ADB app such as LADB, then this switch " +
                "works. Note: hiding the buttons turns on your phone's own gestures, so turn off the " +
                "same edge here if both fire together."
            textSize = 13f
            alpha = 0.75f
        })

        root.addView(section("Size"))
        root.addView(slider("Side edge width", Prefs.EDGE_DP, 12, 48, Prefs.edgeDp(this)))
        root.addView(slider("Bottom strip height", Prefs.BOTTOM_DP, 12, 48, Prefs.bottomDp(this)))

        root.addView(section("Tip"))
        root.addView(TextView(this).apply {
            text = "To get a truly full screen, hide your phone's own navigation buttons " +
                "(look for \"Full screen gestures\", \"Hide navigation bar\" or similar in your " +
                "phone's Settings). This app then provides the swipes. " +
                "If the strips get in the way of an app, make them thinner."
            textSize = 14f
            alpha = 0.8f
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceOn()
        status.text = if (on) "● Active — gestures are working" else "○ Off — turn on \"Full Screen Gesture\" in Accessibility settings"
        status.setTextColor(if (on) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
        refreshNav()
    }

    private fun hasSecure() = checkSelfPermission(secure) == PackageManager.PERMISSION_GRANTED

    private fun refreshNav() {
        val ok = hasSecure()
        val hidden = Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == 2
        navStatus.text = if (ok) "● Switch is ready" else "○ Switch needs one-time setup (see below)"
        navStatus.setTextColor(if (ok) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
        updatingNav = true
        navSwitch.isEnabled = ok
        navSwitch.isChecked = hidden
        updatingNav = false
        navSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingNav) return@setOnCheckedChangeListener
            if (!setNavHidden(checked)) {
                Toast.makeText(this, "Could not change. Do the one-time setup first.", Toast.LENGTH_LONG).show()
                refreshNav()
            }
        }
    }

    private fun setNavHidden(hide: Boolean): Boolean = try {
        val sp = Prefs.sp(this)
        if (hide) {
            val cur = Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
            if (cur != 2) sp.edit().putInt("prev_nav_mode", cur).apply()
            Settings.Secure.putInt(contentResolver, "navigation_mode", 2)
            runCatching { Settings.Global.putInt(contentResolver, "force_fsg_nav_bar", 1) }
        } else {
            Settings.Secure.putInt(contentResolver, "navigation_mode", sp.getInt("prev_nav_mode", 0))
            runCatching { Settings.Global.putInt(contentResolver, "force_fsg_nav_bar", 0) }
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun isServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun toggle(label: String, key: String) = Switch(this).apply {
        text = label
        textSize = 15f
        isChecked = Prefs.bool(this@MainActivity, key)
        setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, checked ->
            Prefs.sp(this@MainActivity).edit().putBoolean(key, checked).apply()
        }
    }

    private fun slider(label: String, key: String, min: Int, max: Int, value: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "$label: $value dp"
            textSize = 15f
            gravity = Gravity.START
            setPadding(0, dp(8), 0, 0)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    title.text = "$label: ${p + min} dp"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    Prefs.sp(this@MainActivity).edit().putInt(key, (s?.progress ?: 0) + min).apply()
                }
            })
        }
        box.addView(title)
        box.addView(bar)
        return box
    }
}
