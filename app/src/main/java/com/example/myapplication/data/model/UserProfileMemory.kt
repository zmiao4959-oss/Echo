package com.example.myapplication.data.model

/**
 * 第三层记忆：用户长期画像。
 * 偏好、习惯、重要目标等长期信息。
 */
data class UserProfileMemory(
    val id: String,
    val key: String,
    val value: String,
    val category: String,          // preference, habit, goal, identity, project, relationship
    val confidence: Float,         // 0.0 - 1.0
    val sourceIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean = true,
    val status: String = "confirmed"  // "confirmed" | "pending"
)
