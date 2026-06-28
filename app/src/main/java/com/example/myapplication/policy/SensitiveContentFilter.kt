package com.example.myapplication.policy

/**
 * 敏感内容过滤器 — Phase J 隐私保护。
 *
 * 检测文本中的身份证、手机号、API key、地址、医疗、财务等敏感信息，
 * 拦截不发送给远程 Embedding API。
 *
 * 纯 Kotlin，零 Android 依赖，JVM 可测。
 */
object SensitiveContentFilter {

    /** 中国身份证号：18 位（17 数字 + 1 数字/X） */
    private val ID_CARD = Regex("\\b[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b")

    /** 中国手机号 */
    private val PHONE = Regex("\\b1[3-9]\\d{9}\\b")

    /** API Key / Token 格式 */
    private val API_KEY = Regex("\\b(sk-[a-zA-Z0-9]{10,}|[a-zA-Z0-9_-]{20,})\\b")

    /** 邮箱 */
    private val EMAIL = Regex("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b")

    /** 详细地址（含 省/市/区/路/号/室 等关键字） */
    private val ADDRESS = Regex("(省|市|区|县|镇|村|路|街|巷|号|栋|单元|室|座|楼|层|门|弄|里|苑|花园|公寓|小区|大厦|广场)[\\d号\\-]")

    /** 医疗/健康关键词上下文 */
    private val MEDICAL = Regex("(诊断|病历|处方|医保|社保|身份证号|病历号|住院号|体检报告|化验单|手术|药物过敏)")

    /** 财务关键词上下文 */
    private val FINANCIAL = Regex("(银行卡|cvv|密码|支付密码|交易密码|网银|支付宝|微信支付|贷款|借款|欠款|余额|转账|汇款)")

    /** 敏感模式组合 */
    private val ALL_PATTERNS = listOf(ID_CARD, PHONE, API_KEY, EMAIL, ADDRESS, MEDICAL, FINANCIAL)

    /**
     * 检查文本是否包含敏感信息。
     * @return true 表示包含敏感内容，不应发送给远程服务。
     */
    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        return ALL_PATTERNS.any { pattern ->
            pattern.containsMatchIn(text)
        }
    }

    /**
     * 返回匹配到的敏感类型列表（用于诊断/日志）。
     */
    fun detectTypes(text: String): List<String> {
        val found = mutableListOf<String>()
        if (ID_CARD.containsMatchIn(text)) found.add("身份证")
        if (PHONE.containsMatchIn(text)) found.add("手机号")
        if (API_KEY.containsMatchIn(text)) found.add("API Key")
        if (EMAIL.containsMatchIn(text)) found.add("邮箱")
        if (ADDRESS.containsMatchIn(text)) found.add("地址")
        if (MEDICAL.containsMatchIn(text)) found.add("医疗")
        if (FINANCIAL.containsMatchIn(text)) found.add("财务")
        return found
    }

    /**
     * 过滤敏感文本：如果包含敏感信息，返回 null（不发送），否则返回原文。
     */
    fun filter(text: String): String? {
        return if (isSensitive(text)) null else text
    }
}
