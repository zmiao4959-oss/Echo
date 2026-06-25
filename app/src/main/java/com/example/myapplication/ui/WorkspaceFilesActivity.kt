package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.memory.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 工作区文件查看与编辑器。
 */
class WorkspaceFilesActivity : ThemedActivity() {

    // ── 列表模式 ──
    private lateinit var listContainer: View
    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var adapter: FileAdapter

    // ── 编辑/查看模式 ──
    private lateinit var editorContainer: View
    private lateinit var textEditorFilename: TextView
    private lateinit var textFileContent: TextView
    private lateinit var editFileContent: EditText
    private lateinit var btnEditSave: ImageButton

    // ── 状态 ──
    private var currentFile: File? = null
    private var currentRelativePath: String? = null
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace_files)

        // 列表模式视图
        listContainer = findViewById(R.id.list_container)
        recycler = findViewById(R.id.recycler_workspace_files)
        textEmpty = findViewById(R.id.text_empty_files)
        findViewById<ImageButton>(R.id.btn_back_list).setOnClickListener { finish() }

        // 编辑/查看模式视图
        editorContainer = findViewById(R.id.editor_container)
        textEditorFilename = findViewById(R.id.text_editor_filename)
        textFileContent = findViewById(R.id.text_file_content)
        editFileContent = findViewById(R.id.edit_file_content)
        btnEditSave = findViewById(R.id.btn_edit_save)
        findViewById<ImageButton>(R.id.btn_back_editor).setOnClickListener { onEditorBack() }
        btnEditSave.setOnClickListener { toggleEditOrSave() }

        // 文件列表
        adapter = FileAdapter { file, relativePath ->
            openFile(file, relativePath)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // 系统返回键/手势处理
        onBackPressedDispatcher.addCallback(this) {
            if (editorContainer.isVisible) {
                onEditorBack()
            } else {
                finish()
            }
        }

        loadFileList()
    }

    private fun loadFileList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = FileStore.listWorkspaceMdFiles()
            withContext(Dispatchers.Main) {
                adapter.submitList(files)
                textEmpty.isVisible = files.isEmpty()
            }
        }
    }

    private fun openFile(file: File, relativePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val content = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            withContext(Dispatchers.Main) {
                currentFile = file
                currentRelativePath = relativePath
                isEditing = false

                textEditorFilename.text = file.name
                textFileContent.text = content
                textFileContent.isVisible = true
                editFileContent.isVisible = false
                btnEditSave.setImageResource(android.R.drawable.ic_menu_edit)

                listContainer.isVisible = false
                editorContainer.isVisible = true
            }
        }
    }

    private fun toggleEditOrSave() {
        if (isEditing) {
            saveFile()
        } else {
            enterEditMode()
        }
    }

    private fun enterEditMode() {
        isEditing = true
        editFileContent.setText(textFileContent.text.toString())
        textFileContent.isVisible = false
        editFileContent.isVisible = true
        btnEditSave.setImageResource(android.R.drawable.ic_menu_save)
        editFileContent.requestFocus()
    }

    private fun saveFile() {
        val path = currentRelativePath ?: return
        val content = editFileContent.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = FileStore.writeWorkspaceFile(path, content)
            withContext(Dispatchers.Main) {
                if (ok) {
                    isEditing = false
                    textFileContent.text = content
                    textFileContent.isVisible = true
                    editFileContent.isVisible = false
                    btnEditSave.setImageResource(android.R.drawable.ic_menu_edit)
                    Toast.makeText(this@WorkspaceFilesActivity, getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
                    loadFileList()
                } else {
                    Toast.makeText(this@WorkspaceFilesActivity, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onEditorBack() {
        if (isEditing && editFileContent.text.toString() != textFileContent.text.toString()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.discard_changes_title))
                .setMessage(getString(R.string.discard_changes_message))
                .setPositiveButton(getString(R.string.discard)) { _, _ -> backToList() }
                .setNegativeButton(getString(R.string.keep_editing), null)
                .show()
        } else {
            backToList()
        }
    }

    private fun backToList() {
        isEditing = false
        currentFile = null
        currentRelativePath = null
        editorContainer.isVisible = false
        listContainer.isVisible = true
    }

    // ── 文件列表 Adapter ──

    class FileAdapter(
        private val onClick: (File, String) -> Unit
    ) : ListAdapter<Pair<String, File>, FileAdapter.ViewHolder>(DiffCallback()) {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textFileName: TextView = view.findViewById(R.id.text_file_name)
            val textFilePath: TextView = view.findViewById(R.id.text_file_path)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workspace_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (relativePath, file) = getItem(position)
            holder.textFileName.text = file.name
            holder.textFilePath.text = if (relativePath.contains("/")) relativePath
                else "${FileStore.workspaceDir.name}/$relativePath"
            holder.itemView.setOnClickListener { onClick(file, relativePath) }
        }

        class DiffCallback : DiffUtil.ItemCallback<Pair<String, File>>() {
            override fun areItemsTheSame(old: Pair<String, File>, new: Pair<String, File>): Boolean =
                old.first == new.first
            override fun areContentsTheSame(old: Pair<String, File>, new: Pair<String, File>): Boolean =
                old.first == new.first && old.second.lastModified() == new.second.lastModified()
        }
    }
}
