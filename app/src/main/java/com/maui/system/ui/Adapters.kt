package com.maui.system.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.AppStorage
import com.maui.system.core.GameEntry
import com.maui.system.core.GameProfile
import com.maui.system.databinding.ItemAppBinding
import com.maui.system.databinding.ItemFileBinding
import com.maui.system.databinding.ItemGameBinding
import com.maui.system.databinding.ItemJunkBinding
import com.maui.system.scanner.JunkCategory
import com.maui.system.scanner.JunkItem
import com.maui.system.util.Format

/** Row model for the built-in file browser. */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val hidden: Boolean = false
)

class JunkAdapter(
    private val onToggle: (JunkItem, Boolean) -> Unit
) : RecyclerView.Adapter<JunkAdapter.VH>() {

    private val items = mutableListOf<JunkItem>()
    private val selected = mutableSetOf<String>()

    fun submit(list: List<JunkItem>) {
        items.clear()
        items += list
        selected.clear()
        // Only auto-select categories that are safe to remove without asking.
        // Hidden files, big files, duplicates and installers may be user data.
        for (i in list) if (i.deletable && safeByDefault(i.category)) selected += i.key
        notifyDataSetChanged()
    }

    fun selectedItems(): List<JunkItem> = items.filter { it.key in selected }

    private fun safeByDefault(c: JunkCategory): Boolean = when (c) {
        JunkCategory.APP_CACHE,
        JunkCategory.THUMBNAILS,
        JunkCategory.TEMP_FILES,
        JunkCategory.LOGS,
        JunkCategory.EMPTY,
        JunkCategory.BROWSER_CACHE,
        JunkCategory.ADB_TEMP -> true
        else -> false
    }

    fun selectAll(on: Boolean) {
        selected.clear()
        if (on) items.filter { it.deletable }.forEach { selected += it.key }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemJunkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.name
        holder.binding.subtitle.text = buildString {
            append(categoryLabel(item.category))
            if (item.duplicateOf != null) append(" • duplicate")
            if (item.isDirectory) append(" • folder")
        }
        holder.binding.size.text = Format.size(item.sizeBytes)
        holder.binding.check.setOnCheckedChangeListener(null)
        holder.binding.check.isChecked = item.key in selected
        holder.binding.check.isEnabled = item.deletable
        holder.binding.check.setOnCheckedChangeListener { _, checked ->
            if (checked) selected += item.key else selected -= item.key
            onToggle(item, checked)
        }
        holder.binding.root.setOnClickListener {
            holder.binding.check.isChecked = !holder.binding.check.isChecked
        }
    }

    private fun categoryLabel(c: JunkCategory): String = when (c) {
        JunkCategory.APP_CACHE -> "App cache"
        JunkCategory.THUMBNAILS -> "Thumbnails"
        JunkCategory.TEMP_FILES -> "Temp file"
        JunkCategory.LOGS -> "Log file"
        JunkCategory.EMPTY -> "Empty folder"
        JunkCategory.HIDDEN -> "Hidden leftover"
        JunkCategory.BIG_FILES -> "Large file"
        JunkCategory.DUPLICATES -> "Duplicate"
        JunkCategory.INSTALLERS -> "Old installer"
        JunkCategory.ADB_TEMP -> "ADB temp"
        JunkCategory.BROWSER_CACHE -> "Browser cache"
    }

    class VH(val binding: ItemJunkBinding) : RecyclerView.ViewHolder(binding.root)
}

class FileAdapter(
    private val onClick: (FileEntry) -> Unit,
    private val onLongClick: (FileEntry) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = mutableListOf<FileEntry>()

    fun submit(list: List<FileEntry>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.binding.glyph.text = if (e.isDirectory) "📁" else "📄"
        holder.binding.title.text = if (e.hidden) ".${e.name}" else e.name
        holder.binding.subtitle.text = Format.date(e.lastModified)
        holder.binding.size.text = if (e.isDirectory) "" else Format.size(e.sizeBytes)
        holder.binding.root.setOnClickListener { onClick(e) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(e)
            true
        }
    }

    class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)
}

class AppAdapter(
    private val onClick: (AppStorage) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private val items = mutableListOf<AppStorage>()

    fun submit(list: List<AppStorage>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.binding.title.text = a.label
        holder.binding.subtitle.text = a.packageName
        holder.binding.size.text = Format.size(a.totalBytes)
        holder.binding.root.setOnClickListener { onClick(a) }
    }

    class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}

class GameAdapter(
    private val onPlay: (GameEntry) -> Unit,
    private val onProfile: (GameEntry) -> Unit,
    /** Icon source; the launcher uses the real package icon. */
    private val iconProvider: (GameEntry, Context) -> Drawable? = { entry, ctx ->
        AppHelper.iconOf(ctx, entry.packageName)
            ?: com.maui.system.ui.widgets.PlaceholderIconDrawable(ctx, entry.label)
    }
) : RecyclerView.Adapter<GameAdapter.VH>() {

    private val items = mutableListOf<GameEntry>()

    fun submit(list: List<GameEntry>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = items[position]
        val ctx = holder.itemView.context
        holder.binding.title.text = g.label
        holder.binding.subtitle.text = if (g.freeFire) {
            ctx.getString(R.string.games_freefire)
        } else {
            g.packageName
        }
        holder.binding.icon.setImageDrawable(iconProvider(g, ctx))
        holder.binding.play.text = ctx.getString(R.string.games_play)
        holder.binding.play.setOnClickListener { onPlay(g) }
        holder.binding.root.setOnClickListener { onPlay(g) }

        holder.binding.profile.text = profileLabel(ctx, g.profile)
        holder.binding.profile.setTextColor(
            when (g.profile) {
                GameProfile.PERFORMANCE -> 0xFF22C55E.toInt()
                GameProfile.BALANCED -> 0xFF2E7BFF.toInt()
                GameProfile.SAVER -> 0xFFF59E0B.toInt()
            }
        )
        holder.binding.profile.setOnClickListener { onProfile(g) }
    }

    private fun profileLabel(ctx: Context, profile: GameProfile): String = when (profile) {
        GameProfile.PERFORMANCE -> ctx.getString(R.string.games_profile_performance)
        GameProfile.BALANCED -> ctx.getString(R.string.games_profile_balanced)
        GameProfile.SAVER -> ctx.getString(R.string.games_profile_saver)
    }

    class VH(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)
}
