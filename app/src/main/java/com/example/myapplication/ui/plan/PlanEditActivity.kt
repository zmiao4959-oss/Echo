package com.example.myapplication.ui.plan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.example.myapplication.ui.ThemedActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.schedule.PlanScheduler
import com.example.myapplication.ui.ThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class PlanEditActivity : ThemedActivity() {

    private var planId: String? = null
    private var editingPlan: EchoPlan? = null
    private var selectedType = "task_reminder"
    private var pickedDate: Long = 0L
    private var pickedTimeHour = 9
    private var pickedTimeMinute = 0

    private val planRepo = PlanRepository()

    private lateinit var etTitle: EditText
    private lateinit var etMessage: EditText
    private lateinit var etRepeat: EditText
    private lateinit var tvPickedDate: TextView
    private lateinit var tvPickedTime: TextView
    private lateinit var switchAutoSpeak: SwitchCompat
    private lateinit var switchEnabled: SwitchCompat
    private lateinit var btnDelete: Button

    // Type chip TextViews
    private lateinit var chipReminder: TextView
    private lateinit var chipCheckin: TextView
    private lateinit var chipMemory: TextView
    private lateinit var chipAutodiary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_edit)

        planId = intent.getStringExtra("plan_id")

        etTitle = findViewById(R.id.et_title)
        etMessage = findViewById(R.id.et_message)
        etRepeat = findViewById(R.id.et_repeat)
        tvPickedDate = findViewById(R.id.tv_picked_date)
        tvPickedTime = findViewById(R.id.tv_picked_time)
        switchAutoSpeak = findViewById(R.id.switch_auto_speak)
        switchEnabled = findViewById(R.id.switch_enabled)
        btnDelete = findViewById(R.id.btn_delete)

        chipReminder = findViewById(R.id.chip_type_reminder)
        chipCheckin = findViewById(R.id.chip_type_checkin)
        chipMemory = findViewById(R.id.chip_type_memory)
        chipAutodiary = findViewById(R.id.chip_type_autodiary)

        // Default time: tomorrow 9:00 AM
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        pickedDate = cal.timeInMillis
        pickedTimeHour = 9
        pickedTimeMinute = 0

        updateDateTimeDisplay()
        setupTypeSelector()
        setupPickers()

        findViewById<Button>(R.id.btn_save).setOnClickListener { savePlan() }
        btnDelete.setOnClickListener { deletePlan() }

        if (planId != null) {
            loadPlan()
        }
    }

    private fun setupTypeSelector() {
        val typeViews = mapOf(
            chipReminder to "task_reminder",
            chipCheckin to "companion_checkin",
            chipMemory to "memory_trigger",
            chipAutodiary to "auto_diary"
        )

        for ((tv, type) in typeViews) {
            tv.setOnClickListener {
                selectedType = type
                updateTypeChipAppearance()
                // 联动 autoSpeak
                switchAutoSpeak.isChecked = when (type) {
                    "task_reminder" -> true
                    else -> false
                }
            }
        }
        updateTypeChipAppearance()
    }

    private fun updateTypeChipAppearance() {
        val activeBg = getDrawable(R.drawable.bg_send_button)
        val inactiveBg = getDrawable(R.drawable.bg_input)
        val activeColor = ThemeColors.onPrimary(this)
        val inactiveColor = ThemeColors.textSecondary(this)

        for ((tv, type) in mapOf(
            chipReminder to "task_reminder",
            chipCheckin to "companion_checkin",
            chipMemory to "memory_trigger",
            chipAutodiary to "auto_diary"
        )) {
            if (type == selectedType) {
                tv.background = activeBg
                tv.setTextColor(activeColor)
            } else {
                tv.background = inactiveBg
                tv.setTextColor(inactiveColor)
            }
        }
    }

    private fun setupPickers() {
        findViewById<View>(R.id.btn_pick_date).setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = pickedDate
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(year, month, day)
                    pickedDate = cal.timeInMillis
                    updateDateTimeDisplay()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        findViewById<View>(R.id.btn_pick_time).setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    pickedTimeHour = hour
                    pickedTimeMinute = minute
                    updateDateTimeDisplay()
                },
                pickedTimeHour,
                pickedTimeMinute,
                true
            ).show()
        }
    }

    private fun loadPlan() {
        lifecycleScope.launch {
            val plan = withContext(Dispatchers.IO) { planRepo.getById(planId!!) }
            if (plan != null) {
                editingPlan = plan
                selectedType = plan.type
                etTitle.setText(plan.title)
                etMessage.setText(plan.message)
                etRepeat.setText(plan.repeatRule ?: "")
                switchAutoSpeak.isChecked = plan.autoSpeak
                switchEnabled.isChecked = plan.enabled
                pickedDate = plan.triggerAt
                val cal = Calendar.getInstance()
                cal.timeInMillis = plan.triggerAt
                pickedTimeHour = cal.get(Calendar.HOUR_OF_DAY)
                pickedTimeMinute = cal.get(Calendar.MINUTE)
                updateDateTimeDisplay()
                updateTypeChipAppearance()
                btnDelete.visibility = View.VISIBLE
            }
        }
    }

    private fun savePlan() {
        val title = etTitle.text.toString().trim()
        val message = etMessage.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        cal.timeInMillis = pickedDate
        cal.set(Calendar.HOUR_OF_DAY, pickedTimeHour)
        cal.set(Calendar.MINUTE, pickedTimeMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val repeatRule = etRepeat.text.toString().trim().ifEmpty { null }

        val plan = EchoPlan(
            id = editingPlan?.id ?: UUID.randomUUID().toString(),
            type = selectedType,
            title = title,
            message = message.ifEmpty { title },
            triggerAt = cal.timeInMillis,
            repeatRule = repeatRule,
            enabled = switchEnabled.isChecked,
            autoSpeak = switchAutoSpeak.isChecked,
            importance = editingPlan?.importance ?: 1,
            tags = editingPlan?.tags ?: emptyList(),
            createdAt = editingPlan?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastTriggeredAt = editingPlan?.lastTriggeredAt
        )

        lifecycleScope.launch {
            if (editingPlan != null) {
                withContext(Dispatchers.IO) {
                    planRepo.update(plan)
                    PlanScheduler.cancel(this@PlanEditActivity, plan.id)
                }
            } else {
                withContext(Dispatchers.IO) { planRepo.add(plan) }
            }
            PlanScheduler.schedule(this@PlanEditActivity, plan)
            Toast.makeText(this@PlanEditActivity, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun deletePlan() {
        AlertDialog.Builder(this)
            .setTitle("删除规划")
            .setMessage("确定要删除「${editingPlan?.title}」吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    planId?.let {
                        PlanScheduler.cancel(this@PlanEditActivity, it)
                        withContext(Dispatchers.IO) { planRepo.delete(it) }
                    }
                    Toast.makeText(this@PlanEditActivity, "已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateDateTimeDisplay() {
        val cal = Calendar.getInstance().apply { timeInMillis = pickedDate }
        val now = Calendar.getInstance()
        val todayStart = now.timeInMillis - (now.timeInMillis % 86_400_000)
        val daysDiff = (cal.timeInMillis - todayStart) / 86_400_000

        tvPickedDate.text = when (daysDiff) {
            0L -> "今天"
            1L -> "明天"
            2L -> "后天"
            else -> {
                val fmt = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINESE)
                fmt.format(java.util.Date(pickedDate))
            }
        }
        tvPickedTime.text = String.format("%02d:%02d", pickedTimeHour, pickedTimeMinute)
    }
}
