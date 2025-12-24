package com.aimetry.di

import com.aimetry.data.local.SessionStorage
import com.aimetry.i18n.LocalizationManager
import org.koin.dsl.module

val i18nModule = module {
    single<LocalizationManager> {
        LocalizationManager(get<SessionStorage>())
    }
}

