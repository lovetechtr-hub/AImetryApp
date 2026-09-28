package com.djmetry.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0)) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { src ->
        digest.usePinned { dst ->
            CC_SHA256(src.addressOf(0), input.size.toUInt(), dst.addressOf(0))
        }
    }
    return digest.toByteArray()
}
