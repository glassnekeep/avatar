package ru.ebica.avatar.camera

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraRequest(
    val someParameter: String
) : Parcelable {
    companion object {
        val requestKey = "camera_request_key"
    }
}