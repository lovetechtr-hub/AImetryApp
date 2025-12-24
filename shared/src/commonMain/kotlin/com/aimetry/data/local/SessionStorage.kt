package com.aimetry.data.local

interface SessionStorage {
    fun saveSessionId(sessionId: String?)
    fun getSessionId(): String?
    fun saveLocale(locale: String)
    fun getLocale(): String?
    fun clear()
}

expect class SessionStorageImpl() : SessionStorage {
    constructor()
}

