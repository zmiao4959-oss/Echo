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
import com.example.myapplication.data.store.AuditLogStore
import com.example.myapplication.policy.MemoryGovernanceService
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.data.store.MigrationManager
import com.example.myapplication.search.SearchIndex
import com.example.myapplication.diagnostics.DataHealthChecker
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

        // 初始化审计日志持久化
        MemoryGovernanceService.onAuditPersist = { entry ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                AuditLogStore.append(listOf(entry))
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val persisted = AuditLogStore.readAll()
            MemoryGovernanceService.loadFromExternal(persisted)
        }

        // Phase H: Schema 迁移 + 搜索索引 + 数据健康检查
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MigrationManager.runMigrations()
            SearchIndex.loadFromDisk()
            SearchIndex.rebuildIfStale()
            // 记一次数据健康检查（结果通过 SettingsActivity 诊断页可见）
            val report = DataHealthChecker.runAllChecks()
            if (report.findings.any { it.severity == DataHealthChecker.Severity.ERROR }) {
                android.util.Log.w("MyApplication", "Data health issues: ${report.summary}")
            }
        }
    }

    companion object {
        lateinit var instance: MyApplication
            private set
    }
}
