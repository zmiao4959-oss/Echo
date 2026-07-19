package com.example.myapplication.ui.journal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.myapplication.data.model.LifeJournalIssue
import java.io.File
import java.io.FileOutputStream

object LifeJournalExporter {
    fun exportPdf(context: Context, issue: LifeJournalIssue): File {
        val dir=(context.getExternalFilesDir("exports") ?: File(context.cacheDir,"exports")).apply{mkdirs()}
        val file=File(dir,"life_journal_${issue.startDate}_${issue.endDate}.pdf")
        val doc=PdfDocument()
        try {
            repeat(LifeJournalPageRenderer.pageCount(issue)){index->
                val info=PdfDocument.PageInfo.Builder(LifeJournalPageRenderer.PAGE_WIDTH,LifeJournalPageRenderer.PAGE_HEIGHT,index+1).create()
                val page=doc.startPage(info);LifeJournalPageRenderer.render(context,page.canvas,issue,index,index*.37f);doc.finishPage(page)
            }
            FileOutputStream(file).use{doc.writeTo(it)}
        } finally { doc.close() }
        return file
    }

    fun exportLongImage(context: Context, issue: LifeJournalIssue): File {
        val count=LifeJournalPageRenderer.pageCount(issue)
        val width=720;val pageHeight=960
        val bitmap=Bitmap.createBitmap(width,pageHeight*count,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val pageBitmap=Bitmap.createBitmap(width,pageHeight,Bitmap.Config.ARGB_8888)
        val pageCanvas=Canvas(pageBitmap)
        repeat(count){index->pageBitmap.eraseColor(android.graphics.Color.TRANSPARENT);LifeJournalPageRenderer.render(context,pageCanvas,issue,index,index*.37f);canvas.drawBitmap(pageBitmap,0f,index*pageHeight.toFloat(),null)}
        val dir=(context.getExternalFilesDir("exports") ?: File(context.cacheDir,"exports")).apply{mkdirs()}
        val file=File(dir,"life_journal_${issue.startDate}_${issue.endDate}.jpg")
        FileOutputStream(file).use{bitmap.compress(Bitmap.CompressFormat.JPEG,94,it)};pageBitmap.recycle();bitmap.recycle();return file
    }

    fun share(context: Context, file: File, mime: String) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileProvider",file)
        val intent=Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        context.startActivity(Intent.createChooser(intent,"保存这期生活志"))
    }
}
