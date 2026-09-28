package com.djmetry.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.ApiException
import com.djmetry.auth.OAuthCancelledException
import com.djmetry.config.AppConfig
import com.djmetry.domain.model.OAuthProvider
import com.djmetry.i18n.Strings
import com.djmetry.ui.components.BrandIcons
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch

/** Реальные фото и обложки для стены, пока не загрузился живой TOP 100. */
private val fallbackWall = listOf(
    "ab67616100005174f150017ca69c8793503c2d4f", "ab676161000051748ebba5e60113b48de8c11f6b",
    "ab67616100005174f4973f7346a2f35e56b63d57", "ab67616d00001e02dd0a40eecd4b13e4c59988da",
    "ab67616100005174f5b8ee60f1f4ee3453aba40b", "ab67616d00001e029367c1ee2eec0bf3a04b4868",
    "ab67616d00001e0287e2f8346625bb6fd20d52be", "ab67616d00001e02dcef905cb144d4867119850b",
    "ab67616d0000b273e4855f57cf9ffcf827fdd6e2",
).map { "https://i.scdn.co/image/$it" }

/** Ключ строки ошибки для показа пользователю; null — пользователь сам закрыл окно входа. */
/** Бренд-слова, которые не переводятся и подсвечиваются в заголовке входа. */
internal const val DJ_HUB = "DJ Hub"

/** [text] с подсвеченным [word] (все вхождения); нет слова — текст как есть. */
internal fun accented(text: String, word: String, color: Color): AnnotatedString = buildAnnotatedString {
    var from = 0
    while (true) {
        val i = text.indexOf(word, from)
        if (i < 0) { append(text.substring(from)); break }
        append(text.substring(from, i))
        withStyle(SpanStyle(color = color)) { append(word) }
        from = i + word.length
    }
}

/** Текст ошибки входа: ключ перевода и, для лимита попыток, число минут до повтора. */
internal data class LoginErrorText(val key: String, val minutes: Int? = null)

internal fun loginError(error: Throwable): LoginErrorText? = when {
    error is OAuthCancelledException -> null
    error is ApiException && error.code == "user_blocked" -> LoginErrorText(Strings.LOGIN_ERROR_BLOCKED)
    error is ApiException && error.isRateLimited ->
        error.retryAfterSeconds?.let { LoginErrorText(Strings.LOGIN_ERROR_RATE_LIMIT, retryMinutes(it)) }
            ?: LoginErrorText(Strings.LOGIN_ERROR_RATE_LIMIT_SOON)
    error is ApiException -> LoginErrorText(Strings.LOGIN_ERROR_FAILED)
    else -> LoginErrorText(Strings.LOGIN_ERROR_NETWORK)
}

/** 61 с → 2 мин: округляем вверх, минимум 1 — чтобы после ожидания вход точно прошёл. */
internal fun retryMinutes(seconds: Int): Int = ((seconds.coerceAtLeast(1) + 59) / 60)

private val AppleWhite = Color(0xFFFFFFFF)
private val FacebookBlue = Color(0xFF1877F2)

/**
 * Экран входа «Стена артистов»: сверху медленно плывёт стена обложек из DJMetry TOP 100,
 * снизу логотип, заголовок и вход через Apple / Google / Facebook.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var inProgress by remember { mutableStateOf<OAuthProvider?>(null) }
    var loginErr by remember { mutableStateOf<LoginErrorText?>(null) }

    val wall by produceState(fallbackWall) {
        container.artistApi.topN(1).onSuccess { top ->
            val images = top.artists.mapNotNull { it.imageUrl }.take(18)
            if (images.size >= 9) value = images
        }
    }

    fun signIn(provider: OAuthProvider) {
        if (inProgress != null) return
        inProgress = provider
        loginErr = null
        scope.launch {
            container.auth.signIn(provider)
                .onSuccess { onSignedIn() }
                .onFailure { loginErr = loginError(it) }
            inProgress = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(DJMetryColors.Background)) {
        ArtistWall(wall, Modifier.fillMaxWidth().height(maxHeight * 0.58f))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 480.dp) // на планшете форма по центру, стена обложек — на весь экран
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Image(DJMetryLogo.Full, contentDescription = "DJMetry", modifier = Modifier.width(170.dp).aspectRatio(DJMetryLogo.ASPECT_RATIO))
            Spacer(Modifier.height(16.dp))
            Text(
                accented(i18n.t(Strings.LOGIN_HUB_TITLE), DJ_HUB, DJMetryColors.Accent),
                color = DJMetryColors.Text,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                i18n.t(Strings.LOGIN_HEADLINE),
                color = DJMetryColors.Muted,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                maxLines = 4,
            )
            Spacer(Modifier.height(22.dp))

            ProviderButton(
                text = i18n.t(Strings.LOGIN_APPLE),
                icon = BrandIcons.Apple,
                container = AppleWhite,
                content = Color.Black,
                loading = inProgress == OAuthProvider.APPLE,
                onClick = { signIn(OAuthProvider.APPLE) },
            )
            Spacer(Modifier.height(10.dp))
            ProviderButton(
                text = i18n.t(Strings.LOGIN_GOOGLE),
                icon = BrandIcons.Google,
                container = DJMetryColors.Panel,
                content = DJMetryColors.Text,
                bordered = true,
                loading = inProgress == OAuthProvider.GOOGLE,
                onClick = { signIn(OAuthProvider.GOOGLE) },
            )
            Spacer(Modifier.height(10.dp))
            ProviderButton(
                text = i18n.t(Strings.LOGIN_FACEBOOK),
                icon = Icons.Filled.Facebook,
                container = FacebookBlue,
                content = Color.White,
                loading = inProgress == OAuthProvider.FACEBOOK,
                onClick = { signIn(OAuthProvider.FACEBOOK) },
            )

            AnimatedVisibility(loginErr != null) {
                Text(
                    loginErr?.let { e -> e.minutes?.let { i18n.tWithArgs(e.key, arrayOf(it)) } ?: i18n.t(e.key) } ?: "",
                    color = DJMetryColors.LowScore,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            LegalText()
        }
    }
}

/**
 * Сетка «стены артистов»: колонки — от ширины (обложка ~110–150 dp на любом экране), с запасом под поворот;
 * рядов в одном проходе — чтобы проход был выше стены (иначе при прокрутке видно конец).
 */
internal data class WallGrid(val columns: Int, val rowsPerSet: Int, val cellDp: Float, val gapDp: Float) {
    val gridWidthDp: Float get() = columns * cellDp + (columns - 1) * gapDp
    val setHeightDp: Float get() = rowsPerSet * (cellDp + gapDp)

    /** На сколько поднять сетку: после поворота на −8° её левый верх опускается на ~7% ширины. */
    val topOverscanDp: Float get() = gridWidthDp * 0.08f + 20f

    /** Обложка в клетке: по кругу, каждый ряд со сдвигом — соседи по вертикали не повторяются. */
    fun imageIndex(row: Int, column: Int, count: Int): Int = if (count == 0) 0 else (row * columns + column + row) % count
}

internal fun wallGrid(widthDp: Float, heightDp: Float, count: Int, gapDp: Float = 10f): WallGrid {
    // Поворот −8° и масштаб: сетка должна быть шире и выше видимой области
    val coverWidth = widthDp * 1.35f
    val cell = (widthDp / 3.4f).coerceIn(110f, 150f)
    val columns = maxOf(3, kotlin.math.ceil((coverWidth + gapDp) / (cell + gapDp)).toInt())
    val gridWidth = columns * cell + (columns - 1) * gapDp
    // Один проход должен закрывать стену целиком вместе с запасом сверху под поворот
    val needHeight = heightDp * 1.2f + gridWidth * 0.08f + 20f + cell
    val minRows = kotlin.math.ceil(needHeight / (cell + gapDp)).toInt()
    val imageRows = if (count == 0) 1 else kotlin.math.ceil(count / columns.toFloat()).toInt()
    return WallGrid(columns, maxOf(minRows, imageRows, 2), cell, gapDp)
}

/** Скорость прокрутки стены, dp в секунду — одинаковая на телефоне, планшете и десктопе. */
private const val WALL_SPEED_DP_PER_SEC = 24f

@Composable
private fun ArtistWall(images: List<String>, modifier: Modifier) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val grid = remember(maxWidth, maxHeight, images.size) { wallGrid(maxWidth.value, maxHeight.value, images.size) }
        val durationMs = (grid.setHeightDp / WALL_SPEED_DP_PER_SEC * 1000).toInt().coerceAtLeast(1000)
        val drift by rememberInfiniteTransition(label = "wall").animateFloat(
            0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "drift",
        )
        val cell = grid.cellDp.dp
        val gap = grid.gapDp.dp
        // Две одинаковые копии прохода подряд; сдвиг ровно на одну копию — бесшовный цикл.
        // requiredSize: сетка больше стены и меряется без ограничения родителя, иначе ряды сжимаются в ноль.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .requiredSize(grid.gridWidthDp.dp, (grid.setHeightDp * 2).dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationZ = -8f
                    translationY = -grid.setHeightDp.dp.toPx() * drift - grid.topOverscanDp.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (images.isNotEmpty()) repeat(2) {
                repeat(grid.rowsPerSet) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(grid.columns) { col -> CoverImage(images[grid.imageIndex(row, col, images.size)], cell, cornerRadius = 14.dp) }
                    }
                }
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to DJMetryColors.Background.copy(alpha = 0.15f),
                    0.55f to DJMetryColors.Background.copy(alpha = 0.55f),
                    0.97f to DJMetryColors.Background,
                )
            )
        )
    }
}

@Composable
private fun ProviderButton(
    text: String,
    icon: ImageVector?,
    container: Color,
    content: Color,
    loading: Boolean,
    onClick: () -> Unit,
    bordered: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp)
            .then(if (bordered) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else Modifier),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LegalText() {
    val i18n = useI18n()
    val uriHandler = LocalUriHandler.current
    val template = i18n.t(Strings.LOGIN_LEGAL)
    val links = mapOf(
        "{terms}" to (i18n.t(Strings.LOGIN_TERMS) to AppConfig.TERMS_URL),
        "{privacy}" to (i18n.t(Strings.LOGIN_PRIVACY) to AppConfig.PRIVACY_URL),
    )
    val text = buildAnnotatedString {
        var rest = template
        while (rest.isNotEmpty()) {
            val next = links.keys.mapNotNull { key -> rest.indexOf(key).takeIf { it >= 0 }?.let { it to key } }.minByOrNull { it.first }
            if (next == null) { append(rest); break }
            append(rest.substring(0, next.first))
            val (label, url) = links.getValue(next.second)
            pushStringAnnotation("url", url)
            withStyle(SpanStyle(color = DJMetryColors.Muted)) { append(label) }
            pop()
            rest = rest.substring(next.first + next.second.length)
        }
    }
    ClickableText(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6F84A8), fontSize = 11.5.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset -> text.getStringAnnotations("url", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) } },
    )
}
