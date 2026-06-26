package com.example.myapplication

import android.app.Application
import com.example.myapplication.config.AppConfig
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.memory.MemoryInitializer
import com.example.myapplication.tools.ToolRegistry
import com.example.myapplication.tools.EchoDiaryTools
import com.example.myapplication.tools.EchoLifeTools
import com.example.myapplication.tools.EchoMemoryTools
import com.example.myapplication.tools.EchoPlanTools
import com.example.myapplication.tools.FileTools
import com.example.myapplication.tools.WebTools
import com.example.myapplication.tools.WeatherTools
import com.example.myapplication.schedule.PlanScheduler
import com.example.myapplication.schedule.ScheduleEngine
import com.example.myapplication.ui.CardTextureManager
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

        // 初始化 Echo 数据目录（Phase 1）
        EchoFileStore.init(this)

        // 初始化卡片纹理管理器（加载自定义纹理列表）
        CardTextureManager.init(this)

        // 初始化工作区文件（SOUL.md, MEMORY.md 等）
        MemoryInitializer.initWorkspace(this)

        // 注册内置工具
        FileTools.registerAll()
        WebTools.registerAll()
        WeatherTools.registerAll()

        // 注册 Echo 工具（Phase 7）
        EchoLifeTools.registerAll()
        EchoDiaryTools.registerAll()
        EchoPlanTools.registerAll()
        EchoMemoryTools.registerAll()

        // 恢复定时闹钟（开机 / 应用启动）
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ScheduleEngine.rescheduleAll(this@MyApplication)
            PlanScheduler.rescheduleAll(this@MyApplication)
        }
    }

    companion object {
        lateinit var instance: MyApplication
            private set
    }
}
