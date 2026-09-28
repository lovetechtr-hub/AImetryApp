package com.djmetry.auth

import java.security.MessageDigest
import java.security.SecureRandom

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

internal actual fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
