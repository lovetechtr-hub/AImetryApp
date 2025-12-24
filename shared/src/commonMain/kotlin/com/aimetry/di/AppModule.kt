package com.aimetry.di

import com.aimetry.api.*
import com.aimetry.api.endpoints.*
import com.aimetry.data.local.SessionStorage
import com.aimetry.data.local.SessionStorageImpl
import com.aimetry.data.repository.*
import io.ktor.client.*
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single { createApiClient("http://127.0.0.1:8080/api") }
    
    single<SessionStorage> { SessionStorageImpl() }
    
    // i18nModule будет добавлен отдельно
    
    single { AuthApi(get(), "http://127.0.0.1:8080/api") }
    single { UserApi(get(), "http://127.0.0.1:8080/api") }
    single { ArtistApi(get(), "http://127.0.0.1:8080/api") }
    
    single { UserRepository(get()) }
    single { ArtistRepository(get()) }
    single { AuthRepository(get(), get()) }
}

fun initKoin() {
    startKoin {
        modules(appModule, com.aimetry.di.i18nModule)
    }
}

