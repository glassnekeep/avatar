package ru.ebica.avatar.camera_response

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EmotionResponse(
    val emotion: Emotion,
    val textToSpeak: String
) : Parcelable {
    companion object {
        const val resultKey = "emotion_result_key"
        const val responseKey = "emotion_response_key"

        fun generateRandomEmotionResult() = EmotionResponse(
            emotion = Emotion.entries.toTypedArray().random(),
            textToSpeak = ""
        )
    }

    enum class Emotion {
        Angry,
        Apathy,
        Crying,
        Happy,
        Loved,
        Sad,
        Normal
    }
}