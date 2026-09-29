package com.djmetry.ui.djmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.MapDjSummary
import com.djmetry.i18n.Strings
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.theme.DJMetryColors

/**
 * «Открыть на карте диджеев · N выступлений в M странах» на карточке артиста — как на сайте: только если у артиста
 * есть выступления на карте (`/map/dj/:id/summary` → `has_points`). Открывает тур этого DJ.
 */
@Composable
fun ArtistMapButton(spotifyArtistId: String) {
    val container = LocalAppContainer.current
    val i18n = useI18n()
    val open = LocalOpenDjMap.current
    val summary by produceState<MapDjSummary?>(null, spotifyArtistId) { value = container.djMap.summary(spotifyArtistId).getOrNull() }
    val s = summary?.takeIf { it.has_points } ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button) { open(spotifyArtistId) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Place, null, tint = DJMetryColors.Accent, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(i18n.t(Strings.MAP_ARTIST_BUTTON), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(i18n.tWithArgs(Strings.MAP_ARTIST_BUTTON_META, arrayOf(s.point_count, s.country_count)), color = DJMetryColors.Muted, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = DJMetryColors.Accent)
    }
}
