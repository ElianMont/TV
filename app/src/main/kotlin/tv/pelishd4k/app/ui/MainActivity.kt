package tv.pelishd4k.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import tv.pelishd4k.app.R
import tv.pelishd4k.app.core.AppSettings
import tv.pelishd4k.app.core.tvFocusScale
import tv.pelishd4k.app.databinding.ActivityMainBinding
import tv.pelishd4k.app.settings

/** Pantalla principal: barra lateral de secciones + contenido. Todo navegable con D-pad. */
class MainActivity : BaseRemoteActivity() {

    override val screenName = "home"

    private enum class Tab { HOME, MOVIES, SERIES, TRENDING, FAVORITES, CONTINUE, SEARCH, SETTINGS }

    private lateinit var b: ActivityMainBinding
    private lateinit var tabViews: Map<Tab, TextView>
    private var selected: Tab? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingShow: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sustituye el tema de pantalla de inicio por el tema normal.
        setTheme(R.style.Theme_PelisHD4K)
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        tabViews = mapOf(
            Tab.HOME to b.tabHome,
            Tab.MOVIES to b.tabMovies,
            Tab.SERIES to b.tabSeries,
            Tab.TRENDING to b.tabTrending,
            Tab.FAVORITES to b.tabFavorites,
            Tab.CONTINUE to b.tabContinue,
            Tab.SEARCH to b.tabSearch,
            Tab.SETTINGS to b.tabSettings,
        )
        for ((tab, view) in tabViews) {
            view.tvFocusScale(1.03f)
            view.setOnClickListener { activate(tab) }
            view.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1f).scaleY(if (hasFocus) 1.03f else 1f).setDuration(120).start()
                // Las secciones internas se previsualizan al enfocar (con retardo para no recargar al pasar).
                if (hasFocus && tab.isInternal()) scheduleShow(tab)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val focus = currentFocus
                val sel = selected
                when {
                    focus != null && !isInSidebar(focus) && sel != null -> tabViews.getValue(sel).requestFocus()
                    sel != null && sel != Tab.HOME -> {
                        show(Tab.HOME)
                        tabViews.getValue(Tab.HOME).requestFocus()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            show(Tab.HOME)
            b.tabHome.requestFocus()
        }
    }

    private fun Tab.isInternal() = this == Tab.HOME || this == Tab.FAVORITES || this == Tab.CONTINUE || this == Tab.SEARCH || this == Tab.SETTINGS

    private fun scheduleShow(tab: Tab) {
        pendingShow?.let { handler.removeCallbacks(it) }
        val r = Runnable { show(tab) }
        pendingShow = r
        handler.postDelayed(r, 180)
    }

    private fun activate(tab: Tab) {
        when (tab) {
            Tab.MOVIES -> openSection(AppSettings.Section.MOVIES)
            Tab.SERIES -> openSection(AppSettings.Section.SERIES)
            Tab.TRENDING -> openSection(AppSettings.Section.TRENDING)
            else -> {
                pendingShow?.let { handler.removeCallbacks(it) }
                show(tab)
                // Pasa el foco al contenido de la sección.
                val content = b.content.getChildAt(0)
                content?.requestFocus()
            }
        }
    }

    fun openSection(section: AppSettings.Section) {
        AppNavigator.openBrowser(this, settings.sectionUrl(section))
    }

    private fun show(tab: Tab) {
        if (selected == tab) return
        selected = tab
        for ((t, v) in tabViews) v.isSelected = (t == tab)
        val fragment: Fragment = when (tab) {
            Tab.HOME -> HomeFragment()
            Tab.FAVORITES -> LibraryFragment.newInstance(LibraryFragment.Mode.FAVORITES)
            Tab.CONTINUE -> LibraryFragment.newInstance(LibraryFragment.Mode.CONTINUE)
            Tab.SEARCH -> SearchFragment()
            Tab.SETTINGS -> SettingsFragment()
            else -> return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commitAllowingStateLoss()
    }

    private fun isInSidebar(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p === b.sidebar) return true
            p = p.parent as? View
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            val f = currentFocus
            val sel = selected
            if (f != null && sel != null && f !is EditText && !isInSidebar(f)) {
                val next = f.focusSearch(View.FOCUS_LEFT)
                if (next == null || isInSidebar(next)) {
                    // Volver siempre a la sección activa, no a la pestaña geométricamente más cercana.
                    tabViews.getValue(sel).requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        pendingShow?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
