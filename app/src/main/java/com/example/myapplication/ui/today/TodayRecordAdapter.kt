package com.example.myapplication.ui.today

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.ThemeColors
import com.example.myapplication.ui.ThemeExperience
import com.example.myapplication.ui.widget.EchoMomentGlyphView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 今日片段列表。
 *
 * 片段正文始终可见，用户为“文字层”选择的纹理直接作为卡片背景。
 * 不再叠加一张只展示纹理的封面，也不需要通过隐藏手势揭示正文。
 */
class TodayRecordAdapter(
    private val onClick: ((LifeRecord, View) -> Unit)? = null,
    @Suppress("unused") private val onDelete: ((String) -> Unit)? = null,
    private val onLikeMicroEcho: ((String) -> Unit)? = null,
    private val onRegenerateMicroEcho: ((String) -> Unit)? = null
) : ListAdapter<LifeRecord, TodayRecordAdapter.ViewHolder>(DiffCallback()) {

    private var mediaPlayer: MediaPlayer? = null
    private val animatedRecordIds = mutableSetOf<String>()

    class ViewHolder(val card: com.google.android.material.card.MaterialCardView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_life_record, parent, false) as com.google.android.material.card.MaterialCardView
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val ctx = holder.itemView.context
        holder.card.animate().cancel()
        holder.card.alpha = 1f
        holder.card.translationY = 0f

        // RecyclerView rows are inflated after the Activity/Fragment theme traversal. Apply the
        // authored surface and chrome here so every recycled row follows the current theme.
        CardTextureManager.apply(holder.card, CardTextureManager.NONE, R.attr.echoSurface)
        ThemeExperience.apply(holder.card)
        val textLayer = holder.card.findViewById<View>(R.id.text_layer)

        val cal = Calendar.getInstance().apply { timeInMillis = record.createdAt }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = CardTextureManager.periodForHour(hour)

        // 只保留正文对应的纹理。若该时段没有单独设置，回退到片段通用纹理。
        val app = ctx.applicationContext as MyApplication
        val config = app.appConfig
        val defaultTextureKey = config.getCardTextureKey(CardTextureManager.LIFE_RECORD)
        val textTextureKey = if (config.lifeRecordUseTimeTexture) {
            config.getLifeRecordTextLayerPeriodTextureKey(period)
                .takeUnless { it == CardTextureManager.NONE }
                ?: defaultTextureKey
        } else {
            defaultTextureKey
        }
        CardTextureManager.applyTextureToView(
            textLayer,
            textTextureKey,
            ThemeColors.surface(ctx),
            config.cardOpacity
        )

        holder.card.findViewById<EchoMomentGlyphView>(R.id.tv_time_icon).setHour(hour)
        holder.card.findViewById<TextView>(R.id.tv_time).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.createdAt))
        holder.card.findViewById<TextView>(R.id.tv_content).text = record.content.trim()

        holder.card.findViewById<TextView>(R.id.tv_linked_plan).visibility =
            if (record.linkedPlanId != null) View.VISIBLE else View.GONE

        val inlineEcho = holder.card.findViewById<View>(R.id.layout_inline_echo)
        val echoText = record.microEcho?.trim().orEmpty()
        inlineEcho.visibility = if (echoText.isNotEmpty()) View.VISIBLE else View.GONE
        holder.card.findViewById<TextView>(R.id.tv_inline_echo).text = "Echo · $echoText"

        val likeEcho = holder.card.findViewById<TextView>(R.id.btn_inline_echo_like)
        likeEcho.text = if (record.microEchoLiked) "♥ 已共鸣" else "♡ 有共鸣"
        likeEcho.isEnabled = !record.microEchoLiked
        likeEcho.alpha = if (record.microEchoLiked) 0.72f else 1f
        likeEcho.setOnClickListener {
            onLikeMicroEcho?.invoke(record.id)
        }
        holder.card.findViewById<TextView>(R.id.btn_inline_echo_regenerate).setOnClickListener {
            onRegenerateMicroEcho?.invoke(record.id)
        }

        val playBtn = holder.card.findViewById<ImageButton>(R.id.btn_play_audio)
        val audioPath = record.audioPath
        if (record.source == "voice" && audioPath != null && File(audioPath).exists()) {
            playBtn.visibility = View.VISIBLE
            playBtn.setImageResource(R.drawable.ic_echo_play)
            playBtn.setOnClickListener { togglePlayback(audioPath, playBtn) }
        } else {
            playBtn.visibility = View.GONE
            playBtn.setOnClickListener(null)
        }

        holder.card.setOnClickListener { onClick?.invoke(record, holder.card) }

        if (animatedRecordIds.add(record.id)) {
            holder.card.alpha = 0f
            holder.card.translationY = 14f * ctx.resources.displayMetrics.density
            holder.card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position.coerceAtMost(4) * 45L))
                .setDuration(320L)
                .start()
        }
    }

    fun refreshAppearance() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    private fun togglePlayback(audioPath: String, playBtn: ImageButton) {
        val ctx = playBtn.context

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playBtn.setImageResource(R.drawable.ic_echo_play)
            return
        }

        mediaPlayer?.release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
                setOnCompletionListener {
                    playBtn.setImageResource(R.drawable.ic_echo_play)
                    release()
                    this@TodayRecordAdapter.mediaPlayer = null
                }
            }
            playBtn.setImageResource(R.drawable.ic_echo_pause)
        } catch (_: Exception) {
            Toast.makeText(ctx, "无法播放语音", Toast.LENGTH_SHORT).show()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    class DiffCallback : DiffUtil.ItemCallback<LifeRecord>() {
        override fun areItemsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem == newItem
    }
}
