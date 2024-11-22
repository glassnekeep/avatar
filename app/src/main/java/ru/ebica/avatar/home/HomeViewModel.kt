package ru.ebica.avatar.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class HomeViewModel : ViewModel() {
    val speechFlow: Flow<String> = flow {
        // Демонстрации ради, в идеальном мире текст присылает бэк
        while(true) {
            emit("Текущее время: ${System.currentTimeMillis()}")
            delay(10000)
        }
    }.flowOn(Dispatchers.IO)
}