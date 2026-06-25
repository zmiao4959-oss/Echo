package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.schedule.ScheduleEngine
import com.example.myapplication.schedule.ScheduleStore
import com.example.myapplication.schedule.ScheduledTask
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 定时提醒列表管理页。
 */
class ScheduleListActivity : ThemedActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_list)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fab_add_schedule).setOnClickListener {
            if (!ScheduleEngine.canScheduleExact(this)) {
                showExactAlarmPermissionDialog()
            } else {
                showEditDialog(null)
            }
        }

        textEmpty = findViewById(R.id.text_empty_schedules)
        recycler = findViewById(R.id.recycler_schedules)

        adapter = ScheduleAdapter(
            onClick = { task -> showEditDialog(task) },
            onLongClick = { task -> showDeleteConfirmation(task) },
            onToggle = { task, enabled ->
                val updated = task.copy(enabled = enabled)
                lifecycleScope.launch {
                    ScheduleStore.update(updated)
                    if (enabled) {
                        ScheduleEngine.schedule(this@ScheduleListActivity, updated)
                    } else {
                        ScheduleEngine.cancel(this@ScheduleListActivity, updated)
                    }
                    loadSchedules()
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        loadSchedules()

        // 检查电池优化，引导用户加白名单
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("电池优化建议")
                .setMessage("为确保定时提醒在后台准时触发，建议将小爪加入电池优化白名单。\n\n点击「去设置」→ 选择「所有应用」→ 找到「小爪」→ 选择「不优化」。")
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    } catch (e: Exception) {
                        // 部分设备不支持直接跳转，打开普通电池优化列表
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                .setNegativeButton("以后再说", null)
                .show()
        }
    }

    private fun loadSchedules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tasks = ScheduleStore.listAll()
            withContext(Dispatchers.Main) {
                adapter.submitList(tasks)
                textEmpty.isVisible = tasks.isEmpty()
            }
        }
    }

    // ── 精确闹钟权限 ──

    private fun showExactAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要闹钟权限")
            .setMessage("定时提醒需要「闹钟和提醒」权限才能准时触发。\n\n点击「去设置」→ 找到「小爪」→ 打开开关即可。")
            .setPositiveButton("去设置") { _, _ ->
                ScheduleEngine.openExactAlarmSettings(this)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 新增 / 编辑弹窗 ──

    private fun showEditDialog(existing: ScheduledTask?) {
        val isEdit = existing != null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        // 时间选择行
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val hourInput = EditText(this).apply {
            hint = "时"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (isEdit) "%02d".format(existing!!.hour) else "")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val colonText = TextView(this).apply {
            text = " : "
            textSize = 20f
        }
        val minuteInput = EditText(this).apply {
            hint = "分"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (isEdit) "%02d".format(existing!!.minute) else "")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pickerBtn = Button(this).apply {
            text = "选时间"
            setOnClickListener { showTimePickerDialog(hourInput, minuteInput) }
        }
        timeRow.addView(hourInput)
        timeRow.addView(colonText)
        timeRow.addView(minuteInput)
        timeRow.addView(pickerBtn)
        container.addView(timeRow)

        // 星期选择
        val daysLabel = TextView(this).apply {
            text = "重复星期："
            textSize = 14f
            setPadding(0, 16, 0, 4)
        }
        container.addView(daysLabel)

        val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val dayChecks = Array(7) { i ->
            CheckBox(this).apply {
                text = dayNames[i]
                isChecked = if (existing != null) (i + 1) in existing.daysOfWeek else true // 默认全选
            }
        }

        val dayGrid = android.widget.GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            for (cb in dayChecks) addView(cb)
        }
        container.addView(dayGrid)

        // 提示词
        val promptLabel = TextView(this).apply {
            text = "提醒内容："
            textSize = 14f
            setPadding(0, 12, 0, 4)
        }
        container.addView(promptLabel)

        val promptInput = EditText(this).apply {
            hint = "例如：提醒我该喝水了"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            setText(existing?.prompt ?: "")
        }
        container.addView(promptInput)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) getString(R.string.edit_schedule) else getString(R.string.add_schedule))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val hour = hourInput.text.toString().toIntOrNull() ?: return@setPositiveButton
                val minute = minuteInput.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (hour !in 0..23 || minute !in 0..59) {
                    Toast.makeText(this, "时间格式不正确", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val days = mutableSetOf<Int>()
                for (i in 0..6) {
                    if (dayChecks[i].isChecked) days.add(i + 1)
                }
                if (days.isEmpty()) {
                    Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val prompt = promptInput.text.toString().trim()
                if (prompt.isBlank()) {
                    Toast.makeText(this, "请输入提醒内容", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    if (isEdit) {
                        val updated = existing!!.copy(
                            hour = hour, minute = minute,
                            daysOfWeek = days, prompt = prompt
                        )
                        ScheduleStore.update(updated)
                        ScheduleEngine.cancel(this@ScheduleListActivity, existing)
                        if (updated.enabled) {
                            ScheduleEngine.schedule(this@ScheduleListActivity, updated)
                        }
                    } else {
                        val task = ScheduledTask(
                            hour = hour, minute = minute,
                            daysOfWeek = days, prompt = prompt
                        )
                        ScheduleStore.add(task)
                        ScheduleEngine.schedule(this@ScheduleListActivity, task)
                    }
                    // 确认反馈：显示下次触发时间
                    val nextTime = ScheduleEngine.calculateNextTrigger(hour, minute, days)
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextTime))
                    val dayStr = SimpleDateFormat("MM-dd EEEE", Locale.CHINA).format(Date(nextTime))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ScheduleListActivity,
                            "已设置提醒：$dayStr $timeStr", Toast.LENGTH_LONG).show()
                        loadSchedules()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showTimePickerDialog(hourInput: EditText, minuteInput: EditText) {
        val currentHour = hourInput.text.toString().toIntOrNull() ?: 8
        val currentMinute = minuteInput.text.toString().toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                hourInput.setText("%02d".format(hour))
                minuteInput.setText("%02d".format(minute))
            },
            currentHour, currentMinute, true
        ).show()
    }

    // ── 删除确认 ──

    private fun showDeleteConfirmation(task: ScheduledTask) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_schedule_title))
            .setMessage(getString(R.string.delete_schedule_message, task.timeFormatted, task.prompt.take(30)))
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                lifecycleScope.launch {
                    ScheduleEngine.cancel(this@ScheduleListActivity, task)
                    ScheduleStore.delete(task.id)
                    withContext(Dispatchers.Main) { loadSchedules() }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Adapter ──

    class ScheduleAdapter(
        private val onClick: (ScheduledTask) -> Unit,
        private val onLongClick: (ScheduledTask) -> Unit,
        private val onToggle: (ScheduledTask, Boolean) -> Unit
    ) : ListAdapter<ScheduledTask, ScheduleAdapter.ViewHolder>(DiffCallback()) {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textTime: TextView = view.findViewById(R.id.text_schedule_time)
            val textPrompt: TextView = view.findViewById(R.id.text_schedule_prompt)
            val textDays: TextView = view.findViewById(R.id.text_schedule_days)
            val switch: androidx.appcompat.widget.SwitchCompat = view.findViewById(R.id.switch_schedule)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_schedule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = getItem(position)
            holder.textTime.text = "%02d:%02d".format(task.hour, task.minute)
            holder.textPrompt.text = task.prompt
            holder.textDays.text = task.daysLabel
            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = task.enabled
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(task, isChecked)
            }
            holder.itemView.setOnClickListener { onClick(task) }
            holder.itemView.setOnLongClickListener {
                onLongClick(task)
                true
            }
        }

        class DiffCallback : DiffUtil.ItemCallback<ScheduledTask>() {
            override fun areItemsTheSame(old: ScheduledTask, new: ScheduledTask): Boolean =
                old.id == new.id
            override fun areContentsTheSame(old: ScheduledTask, new: ScheduledTask): Boolean =
                old == new
        }
    }
}
