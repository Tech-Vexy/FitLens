package com.fitlens.app.ai.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeBackend

/**
 * Thin Firebase AI Logic boundary used by the cloud coaching path.
 *
 * App Check is installed during Application.onCreate before this service is
 * constructed, so requests are attested without embedding an API key.
 */
class FirebaseAiService(
    private val modelName: String = DEFAULT_MODEL_NAME
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-3.8-flash"
    }

    private val model by lazy {
        val firebaseApp = FirebaseApp.getInstance()
        FirebaseAI
            .getInstance(firebaseApp, GenerativeBackend.googleAI())
            .generativeModel(modelName)
    }

    suspend fun generateText(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
        return model.generateContent(prompt).text
            ?: error("Firebase AI Logic returned an empty response")
    }
}
