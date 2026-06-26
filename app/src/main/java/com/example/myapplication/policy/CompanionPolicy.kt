package com.example.myapplication.policy

/**
 * Pure Kotlin: Companion trigger policy and greeting variant selection.
 */
object CompanionPolicy {

    val COMPANION_TYPES = setOf("companion_checkin", "memory_trigger")

    // ── Trigger checks ──

    data class TriggerContext(
        val planType: String,
        val companionEnabled: Boolean,
        val hourOfDay: Int,
        val quietStart: Int,
        val quietEnd: Int,
        val companionAllowVoice: Boolean
    )

    data class TriggerDecision(
        val shouldTrigger: Boolean,
        val suppressVoice: Boolean,
        val reason: String? = null
    )

    fun shouldTrigger(ctx: TriggerContext): TriggerDecision {
        val isCompanionType = ctx.planType in COMPANION_TYPES
        if (!isCompanionType) {
            return TriggerDecision(shouldTrigger = true, suppressVoice = false)
        }
        if (!ctx.companionEnabled) {
            return TriggerDecision(shouldTrigger = false, suppressVoice = false, reason = "Companion disabled")
        }
        val suppressVoice = !ctx.companionAllowVoice
        if (isInQuietHours(ctx.hourOfDay, ctx.quietStart, ctx.quietEnd)) {
            return TriggerDecision(shouldTrigger = false, suppressVoice = false,
                reason = "In quiet hours (${ctx.quietStart}-${ctx.quietEnd})")
        }
        return TriggerDecision(shouldTrigger = true, suppressVoice = suppressVoice)
    }

    fun isInQuietHours(hour: Int, quietStart: Int, quietEnd: Int): Boolean {
        return if (quietStart < quietEnd) hour in quietStart until quietEnd
        else hour >= quietStart || hour < quietEnd
    }

    // ── Greeting variants ──

    data class GreetingContext(
        val hourOfDay: Int,
        val recordCount: Int,
        val hasDiary: Boolean,
        val hasPendingPlan: Boolean,
        val planId: String,
        val todayDate: String
    )

    data class GreetingResult(
        val greeting: String,
        val scenario: String
    )

    // Variant pools (must be declared before usage in methods below)
    val MORNING_WITH_RECORDS_AND_PLANS = listOf(
        "早上好。今天已经有 %d 条记录了，还有计划等着你。",
        "早。%d 条记录，看来状态不错。今天也有计划要完成。",
        "早上好。已经记了 %d 条，今天还有待办事项，慢慢来。"
    )
    val MORNING_WITH_RECORDS = listOf(
        "早上好。今天已经有 %d 条记录了，状态不错。",
        "早。看到你今天已经记了 %d 条，挺有意思的。",
        "今天的 %d 条记录看起来很丰富。有什么想补充的吗？"
    )
    val MORNING_NO_RECORDS = listOf(
        "早上好。新的一天开始了，有什么计划吗？",
        "早。今天会有什么想记录的呢？",
        "新的一天。先喝杯水，慢慢来。"
    )
    val EVENING_WITH_RECORDS_AND_DIARY = listOf(
        "今天记录了 %d 个片段，日记也写好了。好好休息。",
        "日记和 %d 条记录都在了，今天挺充实的。晚安。"
    )
    val EVENING_WITH_RECORDS = listOf(
        "今天记录了 %d 个片段。还有什么想留下的吗？",
        "%d 条记录。如果有想补充的，现在还来得及。"
    )
    val EVENING_NO_RECORDS = listOf(
        "今天过得怎么样？有什么想记录下来的吗？",
        "一天快结束了，有什么想留下的吗？"
    )
    val OTHER_TIME = listOf(
        "今天有什么想留下来的吗？",
        "有什么想记录的吗？随时都可以。"
    )

    fun buildGreeting(ctx: GreetingContext): GreetingResult {
        val seed = (ctx.todayDate + ctx.planId).hashCode()
        return when {
            ctx.hourOfDay in 6..11 -> {
                when {
                    ctx.recordCount > 0 && ctx.hasPendingPlan ->
                        GreetingResult(
                            MORNING_WITH_RECORDS_AND_PLANS[Math.abs(seed) % MORNING_WITH_RECORDS_AND_PLANS.size].format(ctx.recordCount),
                            "morning_records_plans")
                    ctx.recordCount > 0 ->
                        GreetingResult(
                            MORNING_WITH_RECORDS[Math.abs(seed) % MORNING_WITH_RECORDS.size].format(ctx.recordCount),
                            "morning_records")
                    else ->
                        GreetingResult(
                            MORNING_NO_RECORDS[Math.abs(seed) % MORNING_NO_RECORDS.size],
                            "morning_no_records")
                }
            }
            ctx.hourOfDay in 18..23 -> {
                when {
                    ctx.recordCount > 0 && ctx.hasDiary ->
                        GreetingResult(
                            EVENING_WITH_RECORDS_AND_DIARY[Math.abs(seed) % EVENING_WITH_RECORDS_AND_DIARY.size].format(ctx.recordCount),
                            "evening_records_diary")
                    ctx.recordCount > 0 ->
                        GreetingResult(
                            EVENING_WITH_RECORDS[Math.abs(seed) % EVENING_WITH_RECORDS.size].format(ctx.recordCount),
                            "evening_records")
                    else ->
                        GreetingResult(
                            EVENING_NO_RECORDS[Math.abs(seed) % EVENING_NO_RECORDS.size],
                            "evening_no_records")
                }
            }
            else -> GreetingResult(
                OTHER_TIME[Math.abs(seed) % OTHER_TIME.size],
                "other_time")
        }
    }

    fun generateGreetingsForNDays(ctx: GreetingContext, days: Int): Set<String> {
        val results = mutableSetOf<String>()
        for (i in 0 until days) {
            val modified = ctx.copy(todayDate = "2026-06-${26 + i}")
            results.add(buildGreeting(modified).greeting)
        }
        return results
    }
}
