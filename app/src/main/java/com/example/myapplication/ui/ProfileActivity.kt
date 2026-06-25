package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.data.store.DataExporter
import com.example.myapplication.data.store.EchoFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<View>(R.id.entry_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.entry_workspace).setOnClickListener {
            startActivity(Intent(this, WorkspaceFilesActivity::class.java))
        }

        findViewById<View>(R.id.entry_schedules).setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        findViewById<View>(R.id.entry_export).setOnClickListener {
            Toast.makeText(this, "正在导出…", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val zipFile = DataExporter.exportAll(this@ProfileActivity)
                    withContext(Dispatchers.Main) {
                        DataExporter.shareZip(this@ProfileActivity, zipFile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<View>(R.id.entry_clear_data).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空所有数据")
                .setMessage("确定要删除所有生活记录、日记、规划和记忆数据吗？\n\n此操作不可撤销。建议先导出备份。")
                .setPositiveButton("确认清空") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            clearAllData()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProfileActivity, "数据已清空", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProfileActivity, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun clearAllData() {
        // 清空所有 JSON 数据文件（写入空数组）
        val files = listOf(
            EchoFileStore.lifeRecordsFile,
            EchoFileStore.dailyDiariesFile,
            EchoFileStore.plansFile,
            EchoFileStore.memoryCardsFile,
            EchoFileStore.userProfileFile
        )
        for (file in files) {
            if (file.exists()) {
                file.writeText("""{"schemaVersion":1,"items":[]}""", Charsets.UTF_8)
            }
        }
    }
}
