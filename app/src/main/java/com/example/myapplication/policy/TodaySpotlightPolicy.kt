package com.example.myapplication.policy

/** Chooses the single piece of feedback that deserves emphasis on the Today page. */
object TodaySpotlightPolicy {

    const val ECHO_FRESHNESS_MILLIS = 30L * 60L * 1000L

    data class EchoCandidate(
        val text: String? = null,
        val generating: Boolean = false,
        val recordCreatedAtMillis: Long? = null
    )

    data class ForeshadowCandidate(
        val id: String,
        val question: String
    )

    sealed class Spotlight {
        data class MicroEcho(
            val text: String?,
            val generating: Boolean,
            val freshUntilMillis: Long? = null
        ) : Spotlight()

        data class ReturnWelcome(val message: String) : Spotlight()

        data class Foreshadow(
            val id: String,
            val question: String
        ) : Spotlight()

        object Hidden : Spotlight()
    }

    fun select(
        returnWelcome: ReturnWelcomePolicy.Welcome,
        echo: EchoCandidate?,
        foreshadow: ForeshadowCandidate? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Spotlight {
        if (echo?.generating == true) {
            return Spotlight.MicroEcho(text = null, generating = true)
        }

        val echoText = echo?.text?.takeIf { it.isNotBlank() }
        val createdAt = echo?.recordCreatedAtMillis
        if (echoText != null && createdAt != null) {
            val ageMillis = nowMillis - createdAt
            if (ageMillis >= 0L && ageMillis < ECHO_FRESHNESS_MILLIS) {
                return Spotlight.MicroEcho(
                    text = echoText,
                    generating = false,
                    freshUntilMillis = createdAt + ECHO_FRESHNESS_MILLIS
                )
            }
        }

        if (foreshadow != null && foreshadow.question.isNotBlank()) {
            return Spotlight.Foreshadow(foreshadow.id, foreshadow.question)
        }

        if (returnWelcome is ReturnWelcomePolicy.Welcome.WelcomeBack) {
            return Spotlight.ReturnWelcome(returnWelcome.message)
        }

        return Spotlight.Hidden
    }
}
