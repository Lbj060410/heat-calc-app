package com.example.connects.core.network

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: Exception, val message: String = exception.message ?: "Unknown error") : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Exception? = when (this) {
        is Error -> exception
        else -> null
    }
}
