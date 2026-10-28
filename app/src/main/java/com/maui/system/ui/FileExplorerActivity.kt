package com.maui.system.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maui.system.R
import com.maui.system.core.DeviceProfiler
import com.maui.system.databinding.ActivityFileExplorerBinding
import com.maui.system.scanner.JunkCategory
import com.maui.system.scanner.JunkItem
import com.maui.system.scanner.StorageScanner
import com.maui.system.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The built-in file browser. It can browse normal folders and it can list
 * the three things users usually hunt for manually: hidden leftovers,
 * huge files and duplicates.
 */
class FileExplorerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PATH = "path"
        const val MODE_INTERNAL = 0
        const val MODE_HIDDEN = 1
        const val MODE_BIG = 2
        const val MODE_DUPLICATES = 3
    }

    private lateinit var binding: ActivityFileExplorerBinding
    private lateinit var adapter: FileAdapter
    private var mode = MODE_INTERNAL
    private var currentPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getIntExtra(EXTRA_MODE, MODE_INTERNAL)
        currentPath = intent.getStringExtra(EXTRA_PATH)
            ?: (Environment.getExternalStorageDirectory()?.path ?: "/")

        adapter = FileAdapter(
            onClick = { entry ->
                if (entry.isDirectory) openDir(entry.path) else openFile(entry)
            },
            onLongClick = { entry -> confirmDelete(entry) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        when (mode) {
            MODE_HIDDEN -> loadSpecial(JunkCategory.HIDDEN)
            MODE_BIG -> loadSpecial(JunkCategory.BIG_FILES)
            MODE_DUPLICATES -> loadSpecial(JunkCategory.DUPLICATES)
            else -> listDir(currentPath)
        }
    }

    private fun loadSpecial(category: JunkCategory) {
        binding.path.text = getString(
            when (category) {
                JunkCategory.HIDDEN -> R.string.files_hidden
                JunkCategory.BIG_FILES -> R.string.files_big
                else -> R.string.files_dupes
            }
        )
        binding.list.visibility = View.GONE
        lifecycleScope.launch {
            val result = StorageScanner(this@FileExplorerActivity).scan()
            val entries = result.items
                .filter { it.category == category }
                .map {
                    FileEntry(
                        path = it.path,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        sizeBytes = it.sizeBytes,
                        lastModified = it.lastModified,
                        hidden = it.hidden
                    )
                }
            adapter.submit(entries)
            binding.list.visibility = View.VISIBLE
            if (entries.isEmpty()) {
                Toast.makeText(
                    this@FileExplorerActivity,
                    getString(R.string.files_empty),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun listDir(path: String) {
        currentPath = path
        binding.path.text = path
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val dir = File(path)
                val kids: Array<File> = dir.listFiles() ?: emptyArray()
                kids.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).map {
                    FileEntry(
                        path = it.absolutePath,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        sizeBytes = if (it.isDirectory) 0L else it.length(),
                        lastModified = it.lastModified(),
                        hidden = it.name.startsWith(".")
                    )
                }
            }
            adapter.submit(entries)
        }
    }

    private fun openDir(path: String) {
        if (mode != MODE_INTERNAL) {
            // In special mode a directory row shows its contents
            listDir(path)
            return
        }
        listDir(path)
    }

    private fun openFile(entry: FileEntry) {
        try {
            val f = File(entry.path)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.files_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(entry: FileEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.files_delete)
            .setMessage(getString(R.string.files_delete_confirm, entry.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.files_delete) { _, _ ->
                lifecycleScope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        val f = File(entry.path)
                        val size = if (f.isDirectory) dirSize(f) else f.length()
                        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                        if (ok) size else 0L
                    }
                    Toast.makeText(
                        this@FileExplorerActivity,
                        getString(R.string.files_deleted, Format.size(freed)),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (mode == MODE_INTERNAL) listDir(currentPath) else loadSpecial(currentCategory())
                }
            }
            .show()
    }

    private fun currentCategory(): JunkCategory = when (mode) {
        MODE_HIDDEN -> JunkCategory.HIDDEN
        MODE_BIG -> JunkCategory.BIG_FILES
        else -> JunkCategory.DUPLICATES
    }

    private fun dirSize(dir: File): Long {
        var total = 0L
        try {
            dir.walkBottomUp().forEach { if (it.isFile) total += it.length() }
        } catch (t: Throwable) {
        }
        return total
    }
}
