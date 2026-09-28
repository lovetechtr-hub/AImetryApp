package com.djmetry.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Криптостойкие случайные байты платформы. */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** SHA-256 средствами платформы. */
internal expect fun sha256(input: ByteArray): ByteArray

/** Пара PKCE (RFC 7636, S256): verifier остаётся на устройстве, challenge уходит в /auth/:provider/start. */
internal class Pkce private constructor(val verifier: String, val challenge: String) {
    companion object {
        fun generate(): Pkce {
            val verifier = base64Url(secureRandomBytes(64)) // 86 символов, в пределах 43–128
            return Pkce(verifier, challengeFor(verifier))
        }

        /** code_challenge = base64url(sha256(verifier)) без паддинга, 43 символа. */
        fun challengeFor(verifier: String): String = base64Url(sha256(verifier.encodeToByteArray()))

        @OptIn(ExperimentalEncodingApi::class)
        private fun base64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')
    }
}
