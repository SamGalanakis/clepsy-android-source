package com.clepsy.android.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val statusCode: Int?, val message: String?, val throwable: Throwable? = null) : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
}
