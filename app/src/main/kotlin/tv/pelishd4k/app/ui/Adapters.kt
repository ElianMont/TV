package tv.pelishd4k.app.ui

import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import coil.load
import tv.pelishd4k.app.core.tvFocusScale
import tv.pelishd4k.app.data.LibraryItem
import tv.pelishd4k.app.databinding.ItemExploreBinding
import tv.pelishd4k.app.databinding.ItemPosterBinding
import tv.pelishd4k.app.databinding.ItemRecentBinding
import tv.pelishd4k.app.databinding.ItemSettingBinding

class PosterAdapter(
    private val onClick: (LibraryItem) -> Unit,
    private val onLongClick: (LibraryItem) -> Unit,
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    class VH(val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<LibraryItem> = emptyList()

    fun submit(list: List<LibraryItem>) {
        items = list
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        b.root.tvFocusScale(1.06f)
        val radius = parent.resources.displayMetrics.density * 10
        val frame = b.poster.parent as View
        frame.clipToOutline = true
        frame.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.title.text = item.title
        h.b.initial.text = item.title.trim().take(1).uppercase()
        if (item.posterUrl.isNullOrBlank()) {
            h.b.poster.setImageDrawable(null)
        } else {
            h.b.poster.load(item.posterUrl)
        }
        val p = item.progress
        if (p > 0f && p < 0.95f) {
            h.b.progress.visibility = View.VISIBLE
            h.b.progress.progress = (p * 1000).toInt()
        } else {
            h.b.progress.visibility = View.GONE
        }
        h.b.root.setOnClickListener { onClick(item) }
        h.b.root.setOnLongClickListener { onLongClick(item); true }
    }

    override fun onViewRecycled(h: VH) {
        h.b.poster.setImageDrawable(null)
    }
}

data class ExploreItem(
    val title: String,
    val subtitle: String,
    @DrawableRes val background: Int,
    val action: () -> Unit,
)

class ExploreAdapter(private val items: List<ExploreItem>) : RecyclerView.Adapter<ExploreAdapter.VH>() {
    class VH(val b: ItemExploreBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemExploreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        b.root.tvFocusScale(1.07f)
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.root.setBackgroundResource(item.background)
        h.b.title.text = item.title
        h.b.subtitle.text = item.subtitle
        h.b.root.setOnClickListener { item.action() }
    }
}

class RecentAdapter(private val onClick: (String) -> Unit) : RecyclerView.Adapter<RecentAdapter.VH>() {
    class VH(val b: ItemRecentBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<String> = emptyList()

    fun submit(list: List<String>) {
        items = list
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val q = items[position]
        h.b.root.text = q
        h.b.root.setOnClickListener { onClick(q) }
    }
}

class SettingItem(val title: String, val value: () -> String, val onClick: (() -> Unit)?)

class SettingsAdapter(private val items: List<SettingItem>) : RecyclerView.Adapter<SettingsAdapter.VH>() {
    class VH(val b: ItemSettingBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.title.text = item.title
        h.b.value.text = item.value()
        h.b.root.isFocusable = item.onClick != null
        h.b.root.isClickable = item.onClick != null
        h.b.root.setOnClickListener {
            item.onClick?.invoke()
            notifyItemChanged(h.bindingAdapterPosition)
        }
    }
}
