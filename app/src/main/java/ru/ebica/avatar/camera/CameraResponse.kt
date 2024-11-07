package ru.ebica.avatar.camera

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraResponse(
    val someParameter: String
) : Parcelable {
    companion object {
        val responseKey = "camera_response_key"
        val resultKey = "camera_result_key"
    }
}