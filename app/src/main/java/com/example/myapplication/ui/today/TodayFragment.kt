package com.example.myapplication.ui.today

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.MediaRecorder
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.R
import com.example.myapplication.policy.TodayFormationPolicy
import com.example.myapplication.policy.TodaySpotlightPolicy
import com.example.myapplication.policy.WeeklyFootprintPolicy
import com.example.myapplication.policy.TodayDiaryClosurePolicy
import com.example.myapplication.policy.ReturnWelcomePolicy
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.PageTextureManager
import com.example.myapplication.ui.ChatActivity
import com.example.myapplication.ui.MainActivity
import com.example.myapplication.ui.diary.DiaryDetailActivity
import com.example.myapplication.ui.plan.PlanEditActivity
import com.example.myapplication.ui.ThemeColors
import com.example.myapplication.ui.widget.EchoOrbView
import com.example.myapplication.ui.widget.EchoCaptureMotionView
import com.example.myapplication.ui.widget.EchoWeatherView
import com.example.myapplication.tts.TTSParser
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TodayFragment : Fragment() {

    private lateinit var viewModel: TodayViewModel

    // ── UI ──
    private lateinit var tvDate: TextView
    private lateinit var tvWeather: TextView
    private lateinit var layoutWeather: View
    private lateinit var weatherGlyph: EchoWeatherView
    private lateinit var etQuickInput: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var btnRecord: View
    private lateinit var tvRecordCount: TextView
    private lateinit var recyclerRecords: RecyclerView
    private lateinit var tvEmptyRecords: TextView
    private lateinit var cardChatEntry: MaterialCardView
    private lateinit var tvLastReply: TextView
    private lateinit var btnEnterChat: View
    private lateinit var tvDiaryPreview: TextView
    private lateinit var cardInput: MaterialCardView
    private lateinit var cardAiPreview: MaterialCardView
    private lateinit var cardWeeklyFootprint: MaterialCardView
    private lateinit var tvWeeklyStats: TextView
    private lateinit var layoutWeeklyDays: LinearLayout
    private lateinit var tvWeeklyMessage: TextView
    private lateinit var cardTodaySpotlight: MaterialCardView
    private lateinit var tvTodaySpotlightTitle: TextView
    private lateinit var tvTodaySpotlightMessage: TextView
    private lateinit var tvTodaySpotlightAction: TextView
    private lateinit var layoutMicroEchoFeedbackActions: LinearLayout
    private lateinit var tvMicroEchoLike: TextView
    private lateinit var tvMicroEchoRegenerate: TextView
    private lateinit var btnTodayDiaryAction: View
    private lateinit var tvTodayDiaryAction: TextView
    private lateinit var echoOrb: EchoOrbView
    private lateinit var captureMotion: EchoCaptureMotionView
    private var diaryClosure: TodayDiaryClosurePolicy.Closure? = null
    private var currentReturnWelcome: ReturnWelcomePolicy.Welcome =
        ReturnWelcomePolicy.Welcome.Hidden
    private var currentMicroEcho: MicroEchoState = MicroEchoState.Hidden
    private var spotlightExpiryJob: Job? = null
    private var lastSpotlightKey: String? = null
    private var recordAdapter: TodayRecordAdapter? = null
    private val weeklyPulseAnimators = mutableListOf<ObjectAnimator>()
    private var isRecordsExpanded = false
    private var allRecords: List<LifeRecord> = emptyList()

    // 天气
    private val weatherClient = com.example.myapplication.net.HttpClient.instance.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 语音录音（MediaRecorder，不依赖任何第三方语音服务）
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: java.io.File? = null
    private var pendingAudioPath: String? = null  // 录音完成但尚未提交的音频路径

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application as MyApplication)
        )[TodayViewModel::class.java]

        bindViews(view)
        setupQuickRecordEntry()
        setupRecycler()
        setupInput()
        setupDiaryClosureAction()
        setupChatCard()
        observeViewModel()
        applyCardTextures()
        applyPageTexture()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadToday()
        fetchWeather()
        applyCardTextures()
        applyPageTexture()
    }

    private fun applyPageTexture() {
        val config = (requireActivity().application as MyApplication).appConfig
        val key = config.getPageTextureKey(PageTextureManager.TODAY_PAGE)
        PageTextureManager.apply(requireView(), key)
    }

    private fun applyCardTextures() {
        val config = (requireActivity().application as MyApplication).appConfig

        // 对话互动
        CardTextureManager.apply(cardInput, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurface)
        CardTextureManager.apply(cardWeeklyFootprint, config.getCardTextureKey(CardTextureManager.LIFE_RECORD), R.attr.echoSurface)
        CardTextureManager.apply(cardTodaySpotlight, config.getCardTextureKey(CardTextureManager.LIFE_RECORD), R.attr.echoSurfaceVariant)
        CardTextureManager.apply(cardChatEntry, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurface)
        CardTextureManager.apply(cardAiPreview, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurfaceVariant)
        renderTodaySpotlight()
    }

    override fun onDestroyView() {
        spotlightExpiryJob?.cancel()
        spotlightExpiryJob = null
        weeklyPulseAnimators.forEach(ObjectAnimator::cancel)
        weeklyPulseAnimators.clear()
        super.onDestroyView()
    }

    // ── View Binding ──

    private fun bindViews(view: View) {
        tvDate = view.findViewById(R.id.tv_today_date)
        tvWeather = view.findViewById(R.id.tv_weather)
        layoutWeather = view.findViewById(R.id.layout_weather)
        weatherGlyph = view.findViewById(R.id.weather_glyph)
        etQuickInput = view.findViewById(R.id.et_quick_input)
        btnVoice = view.findViewById(R.id.btn_voice)
        btnRecord = view.findViewById(R.id.btn_record)
        tvRecordCount = view.findViewById(R.id.tv_record_count)
        recyclerRecords = view.findViewById(R.id.recycler_records)
        tvEmptyRecords = view.findViewById(R.id.tv_empty_records)
        cardInput = view.findViewById(R.id.card_input)
        cardChatEntry = view.findViewById(R.id.card_chat_entry)
        tvLastReply = view.findViewById(R.id.tv_last_reply)
        btnEnterChat = view.findViewById(R.id.btn_enter_chat)
        tvDiaryPreview = view.findViewById(R.id.tv_diary_preview)
        cardAiPreview = view.findViewById(R.id.card_ai_preview)
        cardWeeklyFootprint = view.findViewById(R.id.card_weekly_footprint)
        tvWeeklyStats = view.findViewById(R.id.tv_weekly_stats)
        layoutWeeklyDays = view.findViewById(R.id.layout_weekly_days)
        tvWeeklyMessage = view.findViewById(R.id.tv_weekly_message)
        cardTodaySpotlight = view.findViewById(R.id.card_today_spotlight)
        tvTodaySpotlightTitle = view.findViewById(R.id.tv_today_spotlight_title)
        tvTodaySpotlightMessage = view.findViewById(R.id.tv_today_spotlight_message)
        tvTodaySpotlightAction = view.findViewById(R.id.tv_today_spotlight_action)
        layoutMicroEchoFeedbackActions = view.findViewById(R.id.layout_micro_echo_feedback_actions)
        tvMicroEchoLike = view.findViewById(R.id.tv_micro_echo_like)
        tvMicroEchoRegenerate = view.findViewById(R.id.tv_micro_echo_regenerate)
        btnTodayDiaryAction = view.findViewById(R.id.btn_today_diary_action)
        tvTodayDiaryAction = view.findViewById(R.id.tv_today_diary_action)
        echoOrb = view.findViewById(R.id.echo_orb)
        captureMotion = view.findViewById(R.id.capture_motion)
    }

    // ── RecyclerView ──

    private fun setupRecycler() {
        recordAdapter = TodayRecordAdapter(
            onClick = { record -> showRecordDetail(record) },
            onDelete = { id -> showDeleteConfirmation(id) },
            onLikeMicroEcho = { id -> viewModel.likeMicroEcho(id) },
            onRegenerateMicroEcho = { id ->
                viewModel.regenerateMicroEcho(id)
                Toast.makeText(requireContext(), "正在换一种回应", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerRecords.layoutManager = LinearLayoutManager(requireContext())
        recyclerRecords.adapter = recordAdapter
    }

    // ── 输入区 ──

    private fun setupInput() {
        btnRecord.setOnClickListener {
            val text = etQuickInput.text.toString().trim()
            val audio = pendingAudioPath

            if (text.isEmpty() && audio == null) return@setOnClickListener

            if (audio != null) {
                // 有录音：优先保存为语音便签，文字作为备注
                val content = if (text.isNotEmpty()) "[语音] $text" else "[语音]"
                viewModel.addRecord(content, "voice", audio)
                playCaptureMotion()
                pendingAudioPath = null
                etQuickInput.text.clear()
                etQuickInput.hint = "记录今天的生活片段…"
                btnVoice.clearColorFilter()
            } else {
                viewModel.addRecord(text, "text")
                playCaptureMotion()
                etQuickInput.text.clear()
            }
        }

        btnVoice.setOnClickListener { toggleVoiceRecord() }
    }

    private fun playCaptureMotion() {
        btnRecord.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        captureMotion.post {
            val overlayLocation = IntArray(2)
            val startLocation = IntArray(2)
            val targetLocation = IntArray(2)
            captureMotion.getLocationOnScreen(overlayLocation)
            btnRecord.getLocationOnScreen(startLocation)
            echoOrb.getLocationOnScreen(targetLocation)
            captureMotion.play(
                startX = startLocation[0] - overlayLocation[0] + btnRecord.width / 2f,
                startY = startLocation[1] - overlayLocation[1] + btnRecord.height / 2f,
                targetX = targetLocation[0] - overlayLocation[0] + echoOrb.width / 2f,
                targetY = targetLocation[1] - overlayLocation[1] + echoOrb.height / 2f,
                onArrive = echoOrb::celebrate
            )
        }
    }

    // ── 对话卡片 ──

    private fun setupChatCard() {
        cardChatEntry.setOnClickListener { enterChat() }
        btnEnterChat.setOnClickListener { enterChat() }
    }

    private fun enterChat(newConversation: Boolean = false) {
        val prefs = requireContext().getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
        val lastId = prefs.getString("last_chat_id", null)
        val chatId = if (newConversation || lastId == null) {
            "android:${System.currentTimeMillis()}"
        } else {
            lastId
        }
        // 记住本次 chatId，下次进来继续
        prefs.edit().putString("last_chat_id", chatId).apply()
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("chat_id", chatId)
        startActivity(intent)
    }

    // ── ViewModel 观察 ──

    private fun observeViewModel() {
        viewModel.loadToday()

        lifecycleScope.launch {
            viewModel.todayDate.collectLatest { date ->
                val display = formatDisplayDate(date)
                tvDate.text = display
            }
        }

        lifecycleScope.launch {
            viewModel.records.collectLatest { records ->
                allRecords = records
                applyRecordFilter()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.weeklyFootprint.collectLatest(::renderWeeklyFootprint)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.diaryClosure.collectLatest { closure ->
                diaryClosure = closure
                renderDiaryClosureAction(closure)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.returnWelcome.collectLatest { welcome ->
                currentReturnWelcome = welcome
                renderTodaySpotlight()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.microEcho.collectLatest { state ->
                currentMicroEcho = state
                renderTodaySpotlight()
            }
        }

        tvRecordCount.setOnClickListener {
            isRecordsExpanded = !isRecordsExpanded
            applyRecordFilter()
        }

        lifecycleScope.launch {
            viewModel.latestSession.collectLatest { session ->
                if (session != null) {
                    val lastAssistant = session.messages.findLast { it.role == "assistant" }
                    val displayText = lastAssistant?.content
                        ?.let(TTSParser::toDisplayText)
                        .orEmpty()
                    if (displayText.isNotBlank()) {
                        tvLastReply.text = "最近: ${displayText.take(60)}…"
                        tvLastReply.visibility = View.VISIBLE
                    } else {
                        tvLastReply.visibility = View.GONE
                    }
                } else {
                    tvLastReply.visibility = View.GONE
                }
            }
        }
    }

    private fun applyRecordFilter() {
        val count = allRecords.size
        val display = if (!isRecordsExpanded && count > 3) allRecords.take(3) else allRecords
        recordAdapter?.submitList(display)

        tvRecordCount.text = when {
            count > 3 && !isRecordsExpanded -> "查看全部 >"
            count > 3 && isRecordsExpanded -> "收起 <"
            else -> ""
        }

        tvEmptyRecords.visibility = if (count == 0) View.VISIBLE else View.GONE
        recyclerRecords.visibility = if (count == 0) View.GONE else View.VISIBLE

        renderTodayFormation(allRecords)
        renderTodaySpotlight()
    }

    private fun renderTodayFormation(records: List<LifeRecord>) {
        val formation = TodayFormationPolicy.build(records)
        val lines = mutableListOf<String>()

        if (formation.recordCount > 0) {
            lines += getString(R.string.today_formation_count, formation.recordCount)
            formation.mood?.let { lines += getString(R.string.today_formation_mood, it) }
            if (formation.themes.isNotEmpty()) {
                lines += getString(
                    R.string.today_formation_themes,
                    formation.themes.joinToString(" · ")
                )
            }
        }
        lines += formation.observation
        tvDiaryPreview.text = lines.joinToString("\n")
    }

    private fun setupQuickRecordEntry() {
        parentFragmentManager.setFragmentResultListener(
            QuickRecordRoute.RESULT_FOCUS_QUICK_INPUT,
            viewLifecycleOwner
        ) { _, _ ->
            focusQuickInput()
        }
    }

    private fun focusQuickInput() {
        etQuickInput.post {
            cardInput.animate().cancel()
            cardInput.animate()
                .scaleX(1.012f)
                .scaleY(1.012f)
                .setDuration(150L)
                .withEndAction {
                    cardInput.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
                }
                .start()
            etQuickInput.requestFocus()
            etQuickInput.setSelection(etQuickInput.text.length)
            cardInput.requestRectangleOnScreen(
                Rect(0, 0, cardInput.width, cardInput.height),
                true
            )
            val keyboard = context?.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? InputMethodManager ?: return@post
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, etQuickInput)
                    .show(WindowInsetsCompat.Type.ime())
            }
            keyboard.showSoftInput(etQuickInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupDiaryClosureAction() {
        btnTodayDiaryAction.setOnClickListener {
            when (val closure = diaryClosure) {
                is TodayDiaryClosurePolicy.Closure.Build -> {
                    (requireActivity() as? MainActivity)?.openDiaryAndGenerateToday()
                }
                is TodayDiaryClosurePolicy.Closure.Ready -> openDiary(closure.diaryId)
                is TodayDiaryClosurePolicy.Closure.ReviewChanges -> openDiary(closure.diaryId)
                TodayDiaryClosurePolicy.Closure.Hidden, null -> Unit
            }
        }
    }

    private fun renderDiaryClosureAction(closure: TodayDiaryClosurePolicy.Closure?) {
        btnTodayDiaryAction.visibility = when (closure) {
            null, TodayDiaryClosurePolicy.Closure.Hidden -> View.GONE
            else -> View.VISIBLE
        }
        tvTodayDiaryAction.text = when (closure) {
            is TodayDiaryClosurePolicy.Closure.Build ->
                getString(R.string.today_diary_closure_build)
            is TodayDiaryClosurePolicy.Closure.Ready ->
                getString(R.string.today_diary_closure_ready)
            is TodayDiaryClosurePolicy.Closure.ReviewChanges ->
                getString(R.string.today_diary_closure_changed, closure.changedRecordCount)
            TodayDiaryClosurePolicy.Closure.Hidden, null -> ""
        }
    }

    private fun openDiary(diaryId: String) {
        startActivity(Intent(requireContext(), DiaryDetailActivity::class.java).apply {
            putExtra("diary_id", diaryId)
        })
    }

    private fun renderTodaySpotlight() {
        val echoCandidate = when (val state = currentMicroEcho) {
            MicroEchoState.Hidden -> null
            is MicroEchoState.Generating -> TodaySpotlightPolicy.EchoCandidate(
                generating = true
            )
            is MicroEchoState.Ready -> TodaySpotlightPolicy.EchoCandidate(
                text = state.text,
                recordCreatedAtMillis = allRecords
                    .firstOrNull { it.id == state.recordId }
                    ?.createdAt
            )
        }
        val spotlight = TodaySpotlightPolicy.select(
            returnWelcome = currentReturnWelcome,
            echo = echoCandidate
        )

        when (spotlight) {
            is TodaySpotlightPolicy.Spotlight.MicroEcho -> {
                cardTodaySpotlight.visibility = View.VISIBLE
                renderMicroEchoSpotlight(spotlight)
            }
            is TodaySpotlightPolicy.Spotlight.ReturnWelcome -> {
                cardTodaySpotlight.visibility = View.VISIBLE
                renderWelcomeSpotlight(spotlight)
            }
            TodaySpotlightPolicy.Spotlight.Hidden -> {
                cardTodaySpotlight.visibility = View.GONE
                setSpotlightInputAction(enabled = false)
            }
        }

        val spotlightKey = when (spotlight) {
            is TodaySpotlightPolicy.Spotlight.MicroEcho ->
                if (spotlight.generating) "echo-generating" else "echo-ready:${spotlight.text.hashCode()}"
            is TodaySpotlightPolicy.Spotlight.ReturnWelcome -> "return-welcome"
            TodaySpotlightPolicy.Spotlight.Hidden -> "hidden"
        }
        if (spotlight !is TodaySpotlightPolicy.Spotlight.Hidden &&
            lastSpotlightKey != null && lastSpotlightKey != spotlightKey
        ) {
            cardTodaySpotlight.alpha = 0.75f
            cardTodaySpotlight.animate().alpha(1f).setDuration(220L).start()
        }
        lastSpotlightKey = spotlightKey
        scheduleSpotlightExpiry(spotlight)
    }

    private fun renderMicroEchoSpotlight(
        spotlight: TodaySpotlightPolicy.Spotlight.MicroEcho
    ) {
        tvTodaySpotlightTitle.setText(R.string.micro_echo_title)
        tvTodaySpotlightAction.visibility = View.GONE
        val ready = currentMicroEcho as? MicroEchoState.Ready
        val actionableEcho = ready?.takeIf { !spotlight.generating && !it.liked }
        layoutMicroEchoFeedbackActions.visibility =
            if (actionableEcho != null) View.VISIBLE else View.GONE
        if (actionableEcho != null) {
            tvMicroEchoLike.setOnClickListener { viewModel.likeMicroEcho(actionableEcho.recordId) }
            tvMicroEchoRegenerate.setOnClickListener {
                viewModel.regenerateMicroEcho(actionableEcho.recordId)
            }
        } else {
            tvMicroEchoLike.setOnClickListener(null)
            tvMicroEchoRegenerate.setOnClickListener(null)
        }
        tvTodaySpotlightMessage.setTextColor(ThemeColors.textPrimary(requireContext()))
        tvTodaySpotlightMessage.text = if (spotlight.generating) {
            getString(R.string.micro_echo_generating)
        } else {
            spotlight.text.orEmpty()
        }
        setSpotlightInputAction(enabled = false)
        applySpotlightTexture(CardTextureManager.CHAT, R.attr.echoSurfaceVariant)
    }

    private fun renderWelcomeSpotlight(
        spotlight: TodaySpotlightPolicy.Spotlight.ReturnWelcome
    ) {
        tvTodaySpotlightTitle.setText(R.string.return_welcome_title)
        tvTodaySpotlightAction.visibility = View.VISIBLE
        layoutMicroEchoFeedbackActions.visibility = View.GONE
        tvTodaySpotlightMessage.setTextColor(ThemeColors.textPrimary(requireContext()))
        tvTodaySpotlightMessage.text = spotlight.message
        setSpotlightInputAction(enabled = true)
        applySpotlightTexture(CardTextureManager.LIFE_RECORD, R.attr.echoSurfaceVariant)
    }

    private fun renderWeeklyFootprint(footprint: WeeklyFootprintPolicy.WeeklyFootprint) {
        tvWeeklyStats.text = if (footprint.activeDays == 0) {
            getString(R.string.weekly_footprint_empty_stats)
        } else {
            getString(
                R.string.weekly_footprint_stats,
                footprint.activeDays,
                footprint.totalRecords
            )
        }
        tvWeeklyMessage.text = footprint.message
        weeklyPulseAnimators.forEach(ObjectAnimator::cancel)
        weeklyPulseAnimators.clear()
        layoutWeeklyDays.removeAllViews()

        footprint.days.forEach { day ->
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = TextView(requireContext()).apply {
                text = day.weekdayLabel
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(ThemeColors.hint(requireContext()))
            }
            val dot = TextView(requireContext()).apply {
                text = when {
                    day.recordCount > 9 -> "9+"
                    day.hasRecord -> day.recordCount.toString()
                    else -> "·"
                }
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(
                    if (day.hasRecord) ThemeColors.onPrimary(requireContext())
                    else ThemeColors.hint(requireContext())
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (day.hasRecord) {
                        setColor(ThemeColors.primary(requireContext()))
                    } else {
                        setColor(ThemeColors.surfaceVariant(requireContext()))
                        setStroke(
                            if (day.weekdayLabel == "今") 2.dpToPx() else 1.dpToPx(),
                            if (day.weekdayLabel == "今") ThemeColors.primary(requireContext())
                            else ThemeColors.border(requireContext())
                        )
                    }
                }
                layoutParams = LinearLayout.LayoutParams(30.dpToPx(), 30.dpToPx()).apply {
                    topMargin = 5.dpToPx()
                }
                contentDescription = if (day.hasRecord) {
                    getString(R.string.weekly_footprint_day_recorded, day.weekdayLabel, day.recordCount)
                } else {
                    getString(R.string.weekly_footprint_day_empty, day.weekdayLabel)
                }
            }
            column.addView(label)
            column.addView(dot)
            layoutWeeklyDays.addView(column)

            if (day.weekdayLabel == "今" && day.hasRecord && animationsEnabled()) {
                ObjectAnimator.ofPropertyValuesHolder(
                    dot,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.10f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.10f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.78f)
                ).apply {
                    duration = 1100L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }.also(weeklyPulseAnimators::add)
            }
        }
    }

    private fun animationsEnabled(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()

    private fun setSpotlightInputAction(enabled: Boolean) {
        cardTodaySpotlight.isClickable = enabled
        cardTodaySpotlight.isFocusable = enabled
        if (enabled) {
            cardTodaySpotlight.setOnClickListener { focusQuickInput() }
        } else {
            cardTodaySpotlight.setOnClickListener(null)
        }
    }

    private fun applySpotlightTexture(textureGroup: String, fallbackAttr: Int) {
        val config = (requireActivity().application as MyApplication).appConfig
        CardTextureManager.apply(
            cardTodaySpotlight,
            config.getCardTextureKey(textureGroup),
            fallbackAttr
        )
    }

    private fun scheduleSpotlightExpiry(spotlight: TodaySpotlightPolicy.Spotlight) {
        spotlightExpiryJob?.cancel()
        val freshUntil = (spotlight as? TodaySpotlightPolicy.Spotlight.MicroEcho)
            ?.freshUntilMillis
            ?: run {
                spotlightExpiryJob = null
                return
            }
        val delayMillis = (freshUntil - System.currentTimeMillis()).coerceAtLeast(1L)
        spotlightExpiryJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(delayMillis)
            spotlightExpiryJob = null
            renderTodaySpotlight()
        }
    }

    // ── 查看记录详情 ──

    private fun showRecordDetail(record: LifeRecord) {
        val sdf = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINESE)
        val timeStr = sdf.format(Date(record.createdAt))

        val sourceLabel = when (record.source) {
            "voice" -> "🎙️ 语音"
            "chat" -> "💬 对话"
            "checkin" -> "👋 问候"
            else -> "✏️ 手动记录"
        }

        val sb = StringBuilder()
        sb.appendLine(record.content)
        sb.appendLine()
        sb.appendLine("$sourceLabel  ·  $timeStr")
        record.mood?.let { sb.appendLine("心情: $it") }
        record.tags.takeIf { it.isNotEmpty() }?.let {
            sb.appendLine("标签: ${it.joinToString(", ")}")
        }
        record.microEcho?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine()
            sb.appendLine("Echo 回声")
            sb.appendLine(it)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("记录详情")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .setNeutralButton("操作") { _, _ -> showRecordActions(record) }
            .show()
    }

    private fun showRecordActions(record: LifeRecord) {
        val planAction = if (record.linkedPlanId == null) "转为计划" else "查看关联计划"
        AlertDialog.Builder(requireContext())
            .setTitle("这段记录")
            .setItems(arrayOf("编辑记录", planAction, "删除记录")) { _, which ->
                when (which) {
                    0 -> showEditRecordDialog(record)
                    1 -> openPlanForRecord(record)
                    2 -> showDeleteConfirmation(record.id)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openPlanForRecord(record: LifeRecord) {
        startActivity(Intent(requireContext(), PlanEditActivity::class.java).apply {
            record.linkedPlanId?.let { putExtra("plan_id", it) }
            putExtra(PlanEditActivity.EXTRA_SOURCE_RECORD_ID, record.id)
            if (record.linkedPlanId == null) {
                putExtra(PlanEditActivity.EXTRA_DRAFT_TITLE, record.content.take(36))
                putExtra(PlanEditActivity.EXTRA_DRAFT_MESSAGE, record.content)
            }
        })
    }

    private fun showEditRecordDialog(record: LifeRecord) {
        val input = EditText(requireContext()).apply {
            setText(record.content)
            minLines = 3
            maxLines = 8
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("编辑这段记录")
            .setMessage("正文修改后，Echo 会根据新内容重新回应。")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val content = input.text.toString().trim()
                if (content.isEmpty()) {
                    Toast.makeText(requireContext(), "记录内容不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateRecordContent(record.id, content)
                    Toast.makeText(requireContext(), "已更新，Echo 正在重新回应", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 删除确认 ──

    private fun showDeleteConfirmation(recordId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除记录")
            .setMessage("确定要删除这条生活记录吗？")
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                viewModel.deleteRecord(recordId)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 日期格式化 ──

    private fun formatDisplayDate(isoDate: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
            val sdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
            parsed?.let { sdf.format(it) } ?: isoDate
        } catch (_: Exception) {
            isoDate
        }
    }

    // ── 天气 ──

    private fun fetchWeather() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
        val cacheTime = prefs.getLong("weather_cache_time", 0L)
        val cacheAge = System.currentTimeMillis() - cacheTime
        val app = requireActivity().application as MyApplication
        val currentCity = app.appConfig.weatherCity
        val cachedCity = prefs.getString("weather_cache_city", null)
        val cacheValid = cacheAge in 0..30 * 60 * 1000L
                && cachedCity == currentCity

        if (cacheValid) {
            val desc = prefs.getString("weather_desc_cn", null)
            val info = prefs.getString("weather_info_v3", null)
            if (desc != null && info != null) {
                renderWeather(desc, info)
                return
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val manualCity = currentCity
                val url = if (manualCity.isNotBlank()) {
                    "https://wttr.in/${java.net.URLEncoder.encode(manualCity, "UTF-8")}?format=j1"
                } else {
                    "https://wttr.in/?format=j1"
                }

                val request = Request.Builder().url(url).build()
                val response = weatherClient.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: return@launch

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val current = json.getAsJsonArray("current_condition")
                    ?.get(0)?.asJsonObject ?: return@launch

                val tempC = current.get("temp_C")?.asString ?: ""
                val descEn = current.getAsJsonArray("weatherDesc")
                    ?.get(0)?.asJsonObject?.get("value")?.asString ?: ""
                val desc = weatherToChinese(descEn)

                // 顶部只保留天气与温度。定位服务返回的英文邻近地名通常不稳定，
                // 对自用首页帮助有限，也会让问候区显得拥挤。
                val infoText = "${tempC}℃"

                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putLong("weather_cache_time", System.currentTimeMillis())
                        .putString("weather_cache_city", currentCity)
                        .putString("weather_desc_cn", desc)
                        .putString("weather_info_v3", infoText)
                        .apply()

                    renderWeather(desc, infoText)
                }
            } catch (_: Exception) {
                // 天气获取失败，静默处理
            }
        }
    }

    private fun renderWeather(description: String, temperature: String) {
        weatherGlyph.setWeatherDescription(description)
        tvWeather.text = "$description · $temperature"
        layoutWeather.contentDescription = "$description，$temperature"
        layoutWeather.visibility = View.VISIBLE
    }

    private fun weatherToChinese(desc: String): String {
        val d = desc.lowercase().trim()
        return when {
            "sunny" in d || "clear" in d -> "晴"
            "partly cloudy" in d -> "多云"
            "cloudy" in d || "overcast" in d -> "阴"
            "rain" in d && "light" in d -> "小雨"
            "rain" in d && "heavy" in d -> "大雨"
            "rain" in d -> "雨"
            "drizzle" in d -> "毛毛雨"
            "thunder" in d -> "雷阵雨"
            "snow" in d && "light" in d -> "小雪"
            "snow" in d && "heavy" in d -> "大雪"
            "snow" in d -> "雪"
            "fog" in d || "mist" in d -> "雾"
            "haze" in d -> "霾"
            else -> desc
        }
    }

    // ── 语音便签（MediaRecorder 录音，无需任何第三方服务）──

    private fun toggleVoiceRecord() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        try {
            val audioDir = java.io.File(requireContext().filesDir, "echo/records/audio")
            audioDir.mkdirs()
            currentAudioFile = java.io.File(audioDir, "voice_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentAudioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnVoice.setColorFilter(0xFFFF4444.toInt())
            Toast.makeText(requireContext(), "🎙 正在录音…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录音启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val audioFile = currentAudioFile
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                // 录音完成，暂存路径，等待用户点击"记录"提交
                pendingAudioPath = audioFile.absolutePath
                btnVoice.setColorFilter(0xFF4CAF50.toInt())  // 绿色 = 录音待提交
                etQuickInput.hint = "语音已录制 · 点「记录」提交（可补充文字）"
                Toast.makeText(requireContext(), "语音已录制 (${formatFileSize(audioFile.length())})，点记录提交", Toast.LENGTH_SHORT).show()
            } else {
                // 录音为空，丢弃
                Toast.makeText(requireContext(), "录音为空，未保存", Toast.LENGTH_SHORT).show()
                btnVoice.clearColorFilter()
                pendingAudioPath = null
            }
            currentAudioFile = null
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录音保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(requireContext(), "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 3001
    }
}
