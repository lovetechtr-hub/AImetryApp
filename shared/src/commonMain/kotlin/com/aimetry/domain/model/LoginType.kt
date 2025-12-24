package com.aimetry.domain.model

sealed class LoginType {
    object User : LoginType()      // Обычный пользователь
    object Artist : LoginType()    // Артист
}

