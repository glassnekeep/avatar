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

        private val emotionWeights = mapOf(
            Emotion.Angry to 5,
            Emotion.Apathy to 10,
            Emotion.Crying to 5,
            Emotion.Happy to 20,
            Emotion.Loved to 5,
            Emotion.Sad to 20,
            Emotion.Normal to 45
        )

        fun generateRandomEmotionResult(): EmotionResponse {
            val weightedEmotions = emotionWeights.flatMap { (emotion, weight) ->
                List(weight) { emotion }
            }
            val randomEmotion = weightedEmotions.random()
            return EmotionResponse(
                emotion = randomEmotion,
                textToSpeak = ""
            )
        }
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