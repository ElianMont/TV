package tv.pelishd4k.app.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import tv.pelishd4k.app.BuildConfig
import tv.pelishd4k.app.R
import tv.pelishd4k.app.core.AppSettings
import tv.pelishd4k.app.core.UrlPolicy
import tv.pelishd4k.app.data.LibraryItem
import tv.pelishd4k.app.databinding.FragmentHomeBinding
import tv.pelishd4k.app.databinding.FragmentLibraryBinding
import tv.pelishd4k.app.databinding.FragmentSearchBinding
import tv.pelishd4k.app.databinding.FragmentSettingsBinding
import tv.pelishd4k.app.remote.RemoteManager
import tv.pelishd4k.app.repo
import tv.pelishd4k.app.settings

// ---------------------------------------------------------------- Inicio

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var b: FragmentHomeBinding? = null
    private lateinit var continueAdapter: PosterAdapter
    private lateinit var favoritesAdapter: PosterAdapter
    private lateinit var historyAdapter: PosterAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        b = binding
        val ctx = requireContext()

        val main = requireActivity() as MainActivity
        val explore = listOf(
            ExploreItem(getString(R.string.tab_movies), "Catálogo", R.drawable.bg_explore_movies) { main.openSection(AppSettings.Section.MOVIES) },
            ExploreItem(getString(R.string.tab_series), "Catálogo", R.drawable.bg_explore_series) { main.openSection(AppSettings.Section.SERIES) },
            ExploreItem(getString(R.string.tab_trending), "Lo más visto", R.drawable.bg_explore_trending) { main.openSection(AppSettings.Section.TRENDING) },
            ExploreItem("Sitio completo", UrlPolicy.hostOf(settings.homeUrl) ?: "", R.drawable.bg_explore_site) { main.openSection(AppSettings.Section.HOME) },
        )
        binding.rowExplore.layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
        binding.rowExplore.adapter = ExploreAdapter(explore)

        continueAdapter = PosterAdapter({ AppNavigator.openLibraryItem(ctx, it) }, { confirmRemove(it, favorite = false) })
        favoritesAdapter = PosterAdapter({ AppNavigator.openLibraryItem(ctx, it) }, { confirmRemove(it, favorite = true) })
        binding.rowContinue.layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
        binding.rowContinue.adapter = continueAdapter
        binding.rowFavorites.layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
        binding.rowFavorites.adapter = favoritesAdapter
        historyAdapter = PosterAdapter({ AppNavigator.openLibraryItem(ctx, it) }, { confirmRemove(it, favorite = false) })
        binding.rowHistory.layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
        binding.rowHistory.adapter = historyAdapter

        binding.btnRemote.setOnClickListener { Dialogs.remoteInfo(ctx) }
        binding.btnPrimary.setOnClickListener { main.openSection(AppSettings.Section.HOME) }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cont = repo.continueWatching()
            val favs = repo.favorites()
            val binding = b ?: return@launch
            continueAdapter.submit(cont)
            favoritesAdapter.submit(favs)
            val hasCont = cont.isNotEmpty()
            val hasFavs = favs.isNotEmpty()
            binding.headContinue.visibility = if (hasCont) View.VISIBLE else View.GONE
            binding.rowContinue.visibility = if (hasCont) View.VISIBLE else View.GONE
            binding.headFavorites.visibility = if (hasFavs) View.VISIBLE else View.GONE
            binding.rowFavorites.visibility = if (hasFavs) View.VISIBLE else View.GONE

            // Historial: lo último visitado que no esté ya en "Continuar viendo".
            val contUrls = cont.map { it.url }.toSet()
            val hist = repo.history().filter { it.url !in contUrls }.take(20)
            historyAdapter.submit(hist)
            binding.headHistory.visibility = if (hist.isNotEmpty()) View.VISIBLE else View.GONE
            binding.rowHistory.visibility = if (hist.isNotEmpty()) View.VISIBLE else View.GONE

            val first = cont.firstOrNull()
            if (first != null) {
                binding.btnPrimary.text = getString(R.string.hero_continue_fmt, first.title)
                binding.btnPrimary.setOnClickListener { AppNavigator.openLibraryItem(requireContext(), first) }
            } else {
                binding.btnPrimary.text = getString(R.string.hero_open_site)
                binding.btnPrimary.setOnClickListener { (requireActivity() as MainActivity).openSection(AppSettings.Section.HOME) }
            }
        }
    }

    private fun confirmRemove(item: LibraryItem, favorite: Boolean) {
        Dialogs.confirm(
            requireContext(),
            item.title,
            getString(if (favorite) R.string.remove_favorite else R.string.remove_history) + "?",
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (favorite) repo.removeFavorite(item.url) else repo.removeFromHistory(item.url)
                reload()
            }
        }
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}

// ---------------------------------------------------------------- Favoritos / Continuar

class LibraryFragment : Fragment(R.layout.fragment_library) {
    enum class Mode { FAVORITES, CONTINUE }

    private var b: FragmentLibraryBinding? = null
    private lateinit var adapter: PosterAdapter
    private val mode: Mode get() = Mode.valueOf(requireArguments().getString(ARG_MODE) ?: Mode.FAVORITES.name)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLibraryBinding.bind(view)
        b = binding
        val ctx = requireContext()
        binding.heading.setText(if (mode == Mode.FAVORITES) R.string.tab_favorites else R.string.tab_continue)
        binding.empty.setText(if (mode == Mode.FAVORITES) R.string.empty_favorites else R.string.empty_continue)
        adapter = PosterAdapter({ AppNavigator.openLibraryItem(ctx, it) }, { confirmRemove(it) })
        binding.grid.layoutManager = GridLayoutManager(ctx, 5)
        binding.grid.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = if (mode == Mode.FAVORITES) repo.favorites() else repo.continueWatching()
            val binding = b ?: return@launch
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmRemove(item: LibraryItem) {
        val fav = mode == Mode.FAVORITES
        Dialogs.confirm(
            requireContext(),
            item.title,
            getString(if (fav) R.string.remove_favorite else R.string.remove_history) + "?",
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (fav) repo.removeFavorite(item.url) else repo.removeFromHistory(item.url)
                reload()
            }
        }
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_MODE = "mode"
        fun newInstance(mode: Mode) = LibraryFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
        }
    }
}

// ---------------------------------------------------------------- Buscador

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var b: FragmentSearchBinding? = null
    private lateinit var adapter: RecentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSearchBinding.bind(view)
        b = binding
        adapter = RecentAdapter { submit(it) }
        binding.recents.layoutManager = LinearLayoutManager(requireContext())
        binding.recents.adapter = adapter
        binding.btnSearch.setOnClickListener { submit(binding.query.text.toString()) }
        binding.query.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit(binding.query.text.toString())
                true
            } else false
        }
    }

    override fun onStart() {
        super.onStart()
        viewLifecycleOwner.lifecycleScope.launch {
            val list = repo.recentSearches()
            val binding = b ?: return@launch
            adapter.submit(list)
            binding.noRecents.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun submit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val url = settings.searchUrl(q)
        if (url == null) {
            Toast.makeText(requireContext(), "La plantilla de búsqueda no es válida (Configuración).", Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { repo.addSearch(q) }
        AppNavigator.openBrowser(requireContext(), url)
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}

// ---------------------------------------------------------------- Configuración

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = SettingsAdapter(buildItems())
    }

    private fun onOff(v: Boolean) = getString(if (v) R.string.on else R.string.off)

    private fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        SettingItem(title, { onOff(get()) }) { set(!get()) }

    private fun editUrl(title: String, get: () -> String, validate: (String) -> Boolean, set: (String) -> Unit) =
        SettingItem(title, get) {
            Dialogs.edit(requireContext(), title, get()) { text ->
                if (validate(text)) {
                    set(text)
                    refreshList()
                } else {
                    Toast.makeText(requireContext(), "Valor no válido", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun <T> choice(title: String, options: List<Pair<String, T>>, get: () -> T, set: (T) -> Unit) =
        SettingItem(title, { options.firstOrNull { it.second == get() }?.first ?: "—" }) {
            val idx = options.indexOfFirst { it.second == get() }.coerceAtLeast(0)
            Dialogs.choice(requireContext(), title, options.map { it.first }, idx) {
                set(options[it].second)
                refreshList()
            }
        }

    private fun refreshList() {
        view?.findViewById<RecyclerView>(R.id.list)?.adapter?.notifyDataSetChanged()
    }

    private fun buildItems(): List<SettingItem> {
        val s = settings
        val ctx = requireContext()
        val auto = getString(R.string.auto)
        return listOf(
            editUrl("Página inicial", { s.homeUrl }, { UrlPolicy.parse(it) != null }) {
                s.homeUrl = UrlPolicy.parse(it)!!.toString()
            },
            editUrl("Ruta de Películas", { s.pathMovies }, { UrlPolicy.resolve(s.homeUrl, it) != null }) { s.pathMovies = it },
            editUrl("Ruta de Series", { s.pathSeries }, { UrlPolicy.resolve(s.homeUrl, it) != null }) { s.pathSeries = it },
            editUrl("Ruta de Tendencias", { s.pathTrending }, { UrlPolicy.resolve(s.homeUrl, it) != null }) { s.pathTrending = it },
            editUrl("Plantilla de búsqueda", { s.searchTemplate }, { it.contains("{query}") && UrlPolicy.buildSearchUrl(it, s.homeUrl, "x") != null }) {
                s.searchTemplate = it
            },
            SettingItem("Dominios permitidos", { s.allowedDomains().joinToString(", ") }) {
                Dialogs.edit(ctx, "Dominios permitidos (separados por comas)", s.extraDomains.replace(",", ", "), "ejemplo.com, otro.net") {
                    s.extraDomains = UrlPolicy.parseDomainList(it).joinToString(",")
                    refreshList()
                }
            },
            toggle("Cursor en el navegador", { s.cursorMode }, { s.cursorMode = it }),
            toggle("User-Agent de escritorio", { s.desktopUserAgent }, { s.desktopUserAgent = it }),
            toggle("Cookies de terceros (reproductores web)", { s.thirdPartyCookies }, { s.thirdPartyCookies = it }),
            choice(
                "Calidad máxima (reproductor nativo)",
                listOf(auto to 0, "4K (2160p)" to 2160, "1080p" to 1080, "720p" to 720, "480p" to 480),
                { s.maxVideoHeight }, { s.maxVideoHeight = it },
            ),
            choice("Salto de avance/retroceso", listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30), { s.seekStepSec }, { s.seekStepSec = it }),
            choice(
                "Idioma de audio preferido",
                listOf("Sistema" to "", "Español" to "es", "Inglés" to "en", "Portugués" to "pt"),
                { s.audioLang }, { s.audioLang = it },
            ),
            choice(
                "Subtítulos por defecto",
                listOf("Desactivados" to "", "Español" to "es", "Inglés" to "en"),
                { s.subtitleLang }, { s.subtitleLang = it },
            ),
            toggle("Reanudar donde lo dejaste", { s.autoResume }, { s.autoResume = it }),
            toggle("Servidor de control remoto (iPhone)", { s.remoteEnabled }, {
                s.remoteEnabled = it
                RemoteManager.refresh(ctx)
            }),
            SettingItem("Emparejar iPhone (QR y PIN)", { "Abrir" }) { Dialogs.remoteInfo(ctx) },
            toggle("Iniciar al encender la TV", { s.autostart }, { s.autostart = it }),
            SettingItem("Borrar cookies y sesiones", { "" }) {
                Dialogs.confirm(ctx, "Borrar cookies y sesiones", "Tendrás que volver a iniciar sesión en los sitios.") {
                    try {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Toast.makeText(ctx, "Cookies borradas", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, getString(R.string.webview_missing), Toast.LENGTH_LONG).show()
                    }
                }
            },
            SettingItem("Borrar historial y progreso", { "" }) {
                Dialogs.confirm(ctx, "Borrar historial y progreso", "Los favoritos se conservan.") {
                    viewLifecycleOwner.lifecycleScope.launch { repo.clearHistory() }
                }
            },
            SettingItem(getString(R.string.search_clear), { "" }) {
                viewLifecycleOwner.lifecycleScope.launch { repo.clearSearches() }
            },
            SettingItem("Versión", { BuildConfig.VERSION_NAME }, null),
        )
    }
}
