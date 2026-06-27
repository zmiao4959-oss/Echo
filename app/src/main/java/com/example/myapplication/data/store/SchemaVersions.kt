package com.example.myapplication.data.store

/**
 * 每个 JSON 文件的当前目标 schema 版本号。
 * 新增字段或改结构时递增对应常量，并在 MigrationManager 注册迁移步骤。
 */
object SchemaVersions {
    const val LIFE_RECORDS = 1
    const val DAILY_DIARIES = 1
    const val PLANS = 1
    const val MEMORY_CARDS = 1
    const val USER_PROFILE = 1
    const val AUDIT_LOG = 1
    const val SEARCH_INDEX = 1
    const val DISCARDED_PROFILES = 1
}
