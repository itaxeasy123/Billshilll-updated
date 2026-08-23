package com.example.accounting.core.common

sealed class AccountingResult<out T> {
    data class Success<out T>(val data: T) : AccountingResult<T>()
    data class Failure(val error: AppError) : AccountingResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): AppError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): AccountingResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AccountingResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): AccountingResult<T> {
        if (this is Failure) action(error)
        return this
    }
}
