package com.djmetry.ui.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.components.SkeletonListRow
import com.djmetry.ui.theme.DJMetryColors

/** Сколько строк показывать скелетоном в таблице рейтинга, пока грузится подборка. */
internal const val RATING_SKELETON_ROWS = 8

/** Подиум-скелетон: три колонки (центр выше), фото, имя, Score, ступень. */
@Composable
internal fun PodiumSkeleton() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel).padding(start = 12.dp, end = 12.dp, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
    ) {
        listOf(false, true, false).forEachIndexed { i, first ->
            Column(Modifier.weight(if (first) 1.15f else 1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.size(if (first) 88.dp else 70.dp), RoundedCornerShape(if (first) 26.dp else 22.dp))
                SkeletonLine(0.75f, 12.dp)
                SkeletonLine(0.4f, 12.dp)
                SkeletonBox(Modifier.fillMaxWidth().height(listOf(52.dp, 70.dp, 40.dp)[i]), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            }
        }
    }
}

/** Строка таблицы-скелетон (альбом, десктоп): место, фото + имя, жанр, Score. */
@Composable
internal fun TableRowSkeleton(seed: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Panel).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RANK_COLUMN), contentAlignment = Alignment.CenterEnd) { SkeletonBox(Modifier.size(22.dp, 14.dp), RoundedCornerShape(5.dp)) }
        Spacer(Modifier.width(12.dp))
        SkeletonBox(Modifier.size(40.dp), RoundedCornerShape(11.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { SkeletonLine(listOf(0.45f, 0.32f, 0.5f, 0.38f)[seed % 4], 13.dp) }
        Box(Modifier.width(200.dp)) { SkeletonLine(listOf(0.5f, 0.35f, 0.6f)[seed % 3], 11.dp) }
        SkeletonBox(Modifier.size(52.dp, 15.dp), RoundedCornerShape(6.dp))
    }
}

/** Строка рейтинга-скелетон под раскладку. */
@Composable
internal fun RatingRowSkeleton(seed: Int, wide: Boolean) {
    if (wide) TableRowSkeleton(seed) else SkeletonListRow(seed)
}
