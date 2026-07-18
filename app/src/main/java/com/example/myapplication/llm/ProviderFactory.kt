package com.example.myapplication.llm

import com.example.myapplication.MyApplication
import com.example.myapplication.net.HttpClient
import okhttp3.OkHttpClient

/**
 * Provider 工厂 — 统一创建 OpenAICompatProvider，注入共享 OkHttpClient。
 */
object ProviderFactory {

    fun createLLMProvider(httpClient: OkHttpClient = HttpClient.instance): LLMProvider {
        val config = MyApplication.instance.appConfig
        return OpenAICompatProvider(
            apiKey = config.llmApiKey,
            baseUrl = config.llmBaseUrl,
            model = config.llmModel,
            httpClient = httpClient
        )
    }
}
