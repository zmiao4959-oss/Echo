package com.example.myapplication.domain

import com.example.myapplication.MyApplication
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.net.HttpClient
import java.util.concurrent.TimeUnit

object ForeshadowInterpreterFactory {
    fun configuredOrNull(): ForeshadowInterpreter? {
        val app = runCatching { MyApplication.instance }.getOrNull() ?: return null
        if (!app.appConfig.isLLMConfigured) return null
        val client = HttpClient.instance.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(22, TimeUnit.SECONDS)
            .build()
        val provider = ProviderFactory.createLLMProvider(client)
        return ForeshadowInterpreter { systemPrompt, userPrompt ->
            provider.chat(
                messages = listOf(
                    LLMMessage("system", systemPrompt),
                    LLMMessage("user", userPrompt)
                ),
                tools = null,
                temperature = 0.1f,
                maxTokens = 300
            ).content
        }
    }
}
