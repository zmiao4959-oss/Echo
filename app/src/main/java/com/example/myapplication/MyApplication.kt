package com.example.myapplication

import android.app.Application
import com.example.myapplication.config.AppConfig
import com.example.myapplication.memory.MemoryInitializer
import com.example.myapplication.tools.ToolRegistry
import com.example.myapplication.tools.FileTools
import com.example.myapplication.tools.WebTools
import com.example.myapplication.tools.WeatherTools
import com.example.myapplication.schedule.ScheduleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {

    lateinit var appConfig: AppConfig
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appConfig = AppConfig(this)

        // 初始化工作区文件（SOUL.md, MEMORY.md 等）
        MemoryInitializer.initWorkspace(this)

        // 注册内置工具
        FileTools.registerAll()
        WebTools.registerAll()
        WeatherTools.registerAll()

        // 恢复定时闹钟（开机 / 应用启动）
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ScheduleEngine.rescheduleAll(this@MyApplication)
        }
    }

    companion object {
        lateinit var instance: MyApplication
            private set
    }
}
