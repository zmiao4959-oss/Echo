package com.example.myapplication.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.model.LifeJournalPeriod
import com.example.myapplication.data.repository.LifeJournalRepository
import com.example.myapplication.memory.LifeJournalBuilder
import com.example.myapplication.ui.journal.LifeJournalExporter
import com.example.myapplication.ui.journal.LifeJournalPageRenderer
import com.example.myapplication.ui.journal.LifeJournalPageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LifeJournalActivity : ThemedActivity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private lateinit var pageView:LifeJournalPageView
    private lateinit var pageLabel:TextView
    private lateinit var status:TextView
    private lateinit var weekChip:TextView
    private lateinit var monthChip:TextView
    private var issue:LifeJournalIssue?=null
    private var period=LifeJournalPeriod.MONTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        loadOrGenerate()
    }

    override fun onDestroy(){scope.cancel();super.onDestroy()}

    private fun buildContent():View{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(10),dp(18),dp(14));setBackgroundColor(ThemeColors.background(this@LifeJournalActivity))}
        ViewCompat.setOnApplyWindowInsetsListener(root){view,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(dp(18),bars.top+dp(10),dp(18),bars.bottom+dp(12));insets}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(label("‹",32f,ThemeColors.textPrimary(this),Gravity.CENTER).apply{setOnClickListener{finish()}},LinearLayout.LayoutParams(dp(44),dp(48)))
        val headText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        headText.addView(label("个人生活志",20f,ThemeColors.textPrimary(this),Gravity.START).apply{setTypeface(typeface,android.graphics.Typeface.BOLD)})
        status=label("正在整理本期材料…",11f,ThemeColors.hint(this),Gravity.START);headText.addView(status)
        header.addView(headText,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        header.addView(label("重编",13f,ThemeColors.primary(this),Gravity.CENTER).apply{setPadding(dp(12),0,dp(12),0);setOnClickListener{generate()}},LinearLayout.LayoutParams(dp(62),dp(42)))
        root.addView(header)

        val selector=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        weekChip=chip("本周"){switchPeriod(LifeJournalPeriod.WEEK)};monthChip=chip("本月"){switchPeriod(LifeJournalPeriod.MONTH)}
        selector.addView(weekChip,LinearLayout.LayoutParams(0,dp(38),1f).apply{marginEnd=dp(5)})
        selector.addView(monthChip,LinearLayout.LayoutParams(0,dp(38),1f).apply{marginStart=dp(5)})
        root.addView(selector,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)).apply{topMargin=dp(4)})
        updateChips()

        val stage=FrameLayout(this).apply{foregroundGravity=Gravity.CENTER}
        pageView=LifeJournalPageView(this).apply{elevation=dp(8).toFloat();onPageChanged={updatePageLabel()}}
        stage.addView(pageView,FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.CENTER))
        root.addView(stage,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f).apply{topMargin=dp(8);bottomMargin=dp(8)})

        val navigation=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        navigation.addView(action("←"){pageView.pageIndex--},LinearLayout.LayoutParams(dp(52),dp(46)))
        pageLabel=label("—",12f,ThemeColors.hint(this),Gravity.CENTER);navigation.addView(pageLabel,LinearLayout.LayoutParams(0,dp(46),1f))
        navigation.addView(action("→"){pageView.pageIndex++},LinearLayout.LayoutParams(dp(52),dp(46)))
        root.addView(navigation)
        val tools=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        tools.addView(action("校订本页"){editPage()},LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(6)})
        tools.addView(action("导出成刊"){showExport()},LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(6)})
        root.addView(tools)
        return root
    }

    private fun loadOrGenerate(){scope.launch{val latest=withContext(Dispatchers.IO){LifeJournalRepository().getLatest()};if(latest!=null){issue=latest;period=latest.period;showIssue(latest)}else generate()}}
    private fun switchPeriod(value:LifeJournalPeriod){if(period==value)return;period=value;updateChips();scope.launch{val w=LifeJournalBuilder.dateWindow(value);val saved=withContext(Dispatchers.IO){LifeJournalRepository().getForPeriod(w.startDate,w.endDate)};if(saved!=null)showIssue(saved)else generate()}}
    private fun generate(){status.text="正在依据原始材料重新编排…";scope.launch{runCatching{withContext(Dispatchers.IO){LifeJournalBuilder.build(period,refineCopy=false)}}.onSuccess{showIssue(it);Toast.makeText(this@LifeJournalActivity,"本期生活志已生成",Toast.LENGTH_SHORT).show()}.onFailure{status.text="生成未完成";Toast.makeText(this@LifeJournalActivity,"生成失败：${it.message}",Toast.LENGTH_LONG).show()}}}
    private fun showIssue(value:LifeJournalIssue){issue=value;period=value.period;pageView.issue=value;status.text="${value.startDate.replace('-','.')}—${value.endDate.replace('-','.')} · 第 ${value.revision} 版";updateChips();updatePageLabel()}
    private fun updatePageLabel(){issue?.let{pageLabel.text=if(pageView.pageIndex==0)"封面" else "${pageView.pageIndex} / ${LifeJournalPageRenderer.pageCount(it)-1}"}}

    private fun editPage(){val current=issue?:return;val index=pageView.pageIndex;val titleInput=EditText(this).apply{setText(when{index==0->current.title;index==1->"本期总述";index<=current.chapters.size+1->current.chapters[index-2].title;else->"刊物说明"});setTextColor(ThemeColors.textPrimary(this@LifeJournalActivity));setHintTextColor(ThemeColors.hint(this@LifeJournalActivity));setSingleLine()}
        val bodyInput=EditText(this).apply{setText(when{index==0->current.subtitle;index==1->current.overview;index<=current.chapters.size+1->current.chapters[index-2].body;else->"资料页由原始材料数量自动生成，不参与改写。"});setTextColor(ThemeColors.textPrimary(this@LifeJournalActivity));setHintTextColor(ThemeColors.hint(this@LifeJournalActivity));gravity=Gravity.TOP;minLines=5;maxLines=12;setPadding(0,dp(12),0,0)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),0);addView(titleInput);addView(bodyInput)}
        if(index==LifeJournalPageRenderer.pageCount(current)-1){Toast.makeText(this,"资料页保持来源口径，不开放改写",Toast.LENGTH_SHORT).show();return}
        AlertDialog.Builder(this).setTitle("校订这一页").setView(box).setNegativeButton("取消",null).setPositiveButton("保存"){_,_->saveEdit(index,titleInput.text.toString(),bodyInput.text.toString())}.show()}

    private fun saveEdit(index:Int,title:String,body:String){val current=issue?:return;val updated=when{index==0->current.copy(title=title.trim(),subtitle=body.trim(),updatedAt=System.currentTimeMillis(),revision=current.revision+1);index==1->current.copy(overview=body.trim(),updatedAt=System.currentTimeMillis(),revision=current.revision+1);else->{val chapters=current.chapters.toMutableList();val ci=index-2;chapters[ci]=chapters[ci].copy(title=title.trim(),body=body.trim());current.copy(chapters=chapters,updatedAt=System.currentTimeMillis(),revision=current.revision+1)}};scope.launch{withContext(Dispatchers.IO){LifeJournalRepository().save(updated)};issue=updated;pageView.issue=updated;pageView.pageIndex=index;status.text="${updated.startDate.replace('-','.')}—${updated.endDate.replace('-','.')} · 第 ${updated.revision} 版";updatePageLabel()}}

    private fun showExport(){val current=issue?:return;AlertDialog.Builder(this).setTitle("导出这期生活志").setItems(arrayOf("PDF · 一页一版","长图 · 连续阅读")){_,which->status.text="正在排版导出…";scope.launch{runCatching{withContext(Dispatchers.IO){if(which==0)LifeJournalExporter.exportPdf(this@LifeJournalActivity,current) else LifeJournalExporter.exportLongImage(this@LifeJournalActivity,current)}}.onSuccess{file->status.text="导出完成 · ${if(which==0)"PDF" else "长图"}";LifeJournalExporter.share(this@LifeJournalActivity,file,if(which==0)"application/pdf" else "image/jpeg")}.onFailure{Toast.makeText(this@LifeJournalActivity,"导出失败：${it.message}",Toast.LENGTH_LONG).show()}}}.show()}

    private fun chip(text:String,onClick:()->Unit)=label(text,13f,ThemeColors.textPrimary(this),Gravity.CENTER).apply{setOnClickListener{onClick()}}
    private fun updateChips(){if(!::weekChip.isInitialized)return;listOf(weekChip to LifeJournalPeriod.WEEK,monthChip to LifeJournalPeriod.MONTH).forEach{(view,value)->view.background=GradientDrawable().apply{cornerRadius=dp(18).toFloat();setColor(if(period==value)ThemeColors.surfaceVariant(this@LifeJournalActivity) else Color.TRANSPARENT);setStroke(dp(1),if(period==value)ThemeColors.primary(this@LifeJournalActivity) else ThemeColors.border(this@LifeJournalActivity))};view.setTextColor(if(period==value)ThemeColors.primary(this) else ThemeColors.hint(this))}}
    private fun action(text:String,onClick:()->Unit)=label(text,14f,ThemeColors.textPrimary(this),Gravity.CENTER).apply{background=GradientDrawable().apply{cornerRadius=dp(16).toFloat();setColor(ThemeColors.surface(this@LifeJournalActivity));setStroke(dp(1),ThemeColors.border(this@LifeJournalActivity))};setOnClickListener{onClick()}}
    private fun label(text:String,size:Float,color:Int,gravity:Int)=TextView(this).apply{this.text=text;textSize=size;setTextColor(color);this.gravity=gravity;includeFontPadding=false}
    private fun dp(value:Int)= (value*resources.displayMetrics.density).toInt()
}
