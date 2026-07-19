package com.example.myapplication.ui.journal

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.ui.ThemeManager

class LifeJournalPageView(context: Context) : View(context) {
    var issue: LifeJournalIssue? = null
        set(value) { field=value; pageIndex=0; updateMotion(); invalidate() }
    var pageIndex: Int = 0
        set(value) { field=value.coerceIn(0,(issue?.let { LifeJournalPageRenderer.pageCount(it)-1 } ?: 0)); invalidate(); onPageChanged?.invoke(field) }
    var onPageChanged: ((Int)->Unit)? = null
    private var downX=0f
    private var motion=false
    private val started=System.currentTimeMillis()
    private val tick=object:Runnable{override fun run(){if(motion&&isAttachedToWindow){invalidate();postDelayed(this,32)}}}

    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); issue?.let { LifeJournalPageRenderer.render(context,canvas,it,pageIndex,(System.currentTimeMillis()-started)/1000f) } }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW=MeasureSpec.getSize(widthMeasureSpec);val maxH=MeasureSpec.getSize(heightMeasureSpec)
        val w=kotlin.math.min(maxW,(maxH*.75f).toInt());val h=(w/0.75f).toInt()
        setMeasuredDimension(w,h)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean { when(event.action){MotionEvent.ACTION_DOWN->downX=event.x;MotionEvent.ACTION_UP->{val dx=event.x-downX;if(kotlin.math.abs(dx)>width*.12f)pageIndex+=if(dx<0)1 else -1}};return true }
    override fun onAttachedToWindow(){super.onAttachedToWindow();updateMotion()}
    override fun onDetachedFromWindow(){motion=false;removeCallbacks(tick);super.onDetachedFromWindow()}
    private fun updateMotion(){removeCallbacks(tick);motion=issue?.let{ThemeManager.isDynamic(it.themeKey)}==true;if(motion)post(tick)}
}
