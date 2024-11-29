package ru.ebica.avatar.network

import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File


interface ApiService {
    @Multipart
    @POST("analyze-photo/")
    fun analyzePhoto(
        @Part file: MultipartBody.Part
    ): Call<AnalyzePhotoResponse>
}

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(ApiService::class.java)
    }
}

fun prepareFilePart(file: File): MultipartBody.Part {
    val requestFile = RequestBody.create(MediaType.parse("image/png"), file)
    return MultipartBody.Part.createFormData("file", file.name, requestFile)
}

data class Probabilities(
    val anger: Double,
    val contempt: Double,
    val disgust: Double,
    val fear: Double,
    val happiness: Double,
    val neutral: Double,
    val sadness: Double,
    val surprise: Double
)

data class AnalyzePhotoResponse(
    val image: String,
    val probabilities: Probabilities
)