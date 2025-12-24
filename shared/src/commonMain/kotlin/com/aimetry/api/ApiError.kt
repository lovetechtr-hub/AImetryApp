package com.aimetry.api

sealed class ApiError {
    object Unauthorized : ApiError()
    object NotFound : ApiError()
    object NetworkError : ApiError()
    data class BadRequest(val message: String) : ApiError()
    data class ServerError(val message: String) : ApiError()
    data class Unknown(val message: String) : ApiError()
}

// Исключения для управления артистами
class ArtistLimitReachedException(val maxCount: Int) : Exception("Достигнут лимит: максимум $maxCount артистов")
class ArtistNotVerifiedException : Exception("Артист не прошел верификацию каталога")
class ArtistNotDJException : Exception("Артист не является DJ")

