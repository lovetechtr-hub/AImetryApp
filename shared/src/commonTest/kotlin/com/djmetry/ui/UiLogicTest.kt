package com.djmetry.ui

import com.djmetry.api.ApiException
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.i18n.Strings
import com.djmetry.ui.navigation.Screen
import com.djmetry.ui.navigation.startDestination
import com.djmetry.ui.screens.formatDelta
import com.djmetry.ui.screens.formatScore
import com.djmetry.ui.screens.SwipeAction
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.screens.swipeDecision
import com.djmetry.data.repository.VoteLimitException
import com.djmetry.ui.screens.LoginErrorText
import com.djmetry.ui.screens.loginError
import com.djmetry.ui.screens.retryMinutes
import kotlin.test.*

class UiLogicTest {

    @Test
    fun startDestinationRouting() {
        assertEquals(Screen.Main, startDestination(isSignedIn = true, onboardingSeen = false))
        assertEquals(Screen.Onboarding, startDestination(isSignedIn = false, onboardingSeen = false))
        assertEquals(Screen.Login, startDestination(isSignedIn = false, onboardingSeen = true))
    }

    @Test
    fun loginErrorMessages() {
        assertNull(loginError(OAuthCancelledException()), "отмену пользователем не показываем как ошибку")
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_BLOCKED), loginError(ApiException(0, "user_blocked", null)))
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_FAILED), loginError(ApiException(400, "invalid_or_expired_code", null)))
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_NETWORK), loginError(IllegalStateException("no network")))
    }

    /** Лимит попыток входа: понятный текст с минутами, а не «не удалось войти». */
    @Test
    fun rateLimitIsExplainedWithMinutes() {
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_RATE_LIMIT, 15), loginError(ApiException(0, "too_many_requests", null, retryAfterSeconds = 900)))
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_RATE_LIMIT, 1), loginError(ApiException(0, "too_many_oauth_init_requests", null, retryAfterSeconds = 30)))
        assertEquals(LoginErrorText(Strings.LOGIN_ERROR_RATE_LIMIT_SOON), loginError(ApiException(429, null, null)), "без retry_after — «через несколько минут»")
        assertEquals(1, retryMinutes(0))
        assertEquals(1, retryMinutes(60))
        assertEquals(2, retryMinutes(61))
    }

    @Test
    fun scoreFormatting() {
        assertEquals("53.16", formatScore(53.1649))
        assertEquals("48.70", formatScore(48.7))
        assertEquals("0.05", formatScore(0.049))
        assertEquals("100.00", formatScore(99.999))
    }

    @Test
    fun deltaFormatting() {
        assertEquals("6.45", formatDelta(6.4512))
        assertEquals("33", formatDelta(33.0))
    }

    @Test
    fun swipeDirections() {
        assertEquals(SwipeAction.Follow, swipeDecision(dx = 120f, dy = 0f, threshold = 100f))
        assertEquals(SwipeAction.Skip, swipeDecision(dx = -120f, dy = 30f, threshold = 100f))
        assertEquals(SwipeAction.Vote, swipeDecision(dx = 20f, dy = -130f, threshold = 100f))
        assertNull(swipeDecision(dx = 60f, dy = -60f, threshold = 100f), "короткий жест возвращает карточку")
    }

    @Test
    fun deckActionErrors() {
        assertEquals(Strings.TOAST_VOTE_LIMIT, actionErrorKey(VoteLimitException(3)))
        assertEquals(Strings.TOAST_NEED_LOGIN, actionErrorKey(ApiException(401, "unauthorized", null)))
        assertEquals(Strings.TOAST_FAILED, actionErrorKey(IllegalStateException()))
        assertEquals(Strings.TOAST_SLOW_DOWN, actionErrorKey(ApiException(429, "rate_limited", null)))
        assertEquals(Strings.TOAST_VOTE_NO_RATING, actionErrorKey(ApiException(400, "no_rating", null)))
        assertEquals(Strings.TOAST_VOTE_LEGEND, actionErrorKey(ApiException(400, "legend_not_votable", null)))
        assertEquals(Strings.TOAST_VOTE_FOLLOW_FIRST, actionErrorKey(ApiException(400, "not_following", null)))
    }

    @Test
    fun djHubIsAccentedInLoginTitle() {
        val green = androidx.compose.ui.graphics.Color.Green
        val t = com.djmetry.ui.screens.accented("Твой DJ Hub.", "DJ Hub", green)
        assertEquals("Твой DJ Hub.", t.text)
        val span = t.spanStyles.single()
        assertEquals("DJ Hub", t.text.substring(span.start, span.end))
        assertEquals(green, span.item.color)
        assertTrue(com.djmetry.ui.screens.accented("Sin marca", "DJ Hub", green).spanStyles.isEmpty())
    }
}
