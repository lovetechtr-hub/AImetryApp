package com.djmetry.auth

import kotlin.test.*

class PkceTest {
    @Test
    fun challengeMatchesRfc7636Example() {
        // RFC 7636, Appendix B
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun generatedPairIsValidS256() {
        val pkce = Pkce.generate()
        assertTrue(pkce.verifier.length in 43..128, "verifier length ${pkce.verifier.length}")
        assertTrue(pkce.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "verifier must be base64url")
        assertEquals(43, pkce.challenge.length)
        assertFalse(pkce.challenge.contains('='))
        assertEquals(Pkce.challengeFor(pkce.verifier), pkce.challenge)
    }

    @Test
    fun verifiersAreRandom() {
        assertNotEquals(Pkce.generate().verifier, Pkce.generate().verifier)
    }
}
