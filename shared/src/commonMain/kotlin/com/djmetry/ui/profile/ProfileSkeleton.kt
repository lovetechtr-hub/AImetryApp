package com.djmetry.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.components.SkeletonCard
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.theme.DJMetryColors

/** Скелетон профиля: герой, метрики, плитки, карточки — по раскладке готового экрана. */
@Composable
internal fun ProfileSkeleton(layout: LayoutClass) {
    val hero: @Composable (Modifier) -> Unit = { m -> SkeletonBox(m.fillMaxWidth().height(320.dp), RoundedCornerShape(28.dp)) }
    val metrics: @Composable (Int) -> Unit = { cols ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(cols) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(0.6f, 18.dp); SkeletonLine(0.8f, 10.dp)
                }
            }
        }
    }
    val tiles: @Composable (Int) -> Unit = { cols ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(cols) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(DJMetryColors.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SkeletonBox(Modifier.size(24.dp), RoundedCornerShape(7.dp)); SkeletonLine(0.7f, 13.dp); SkeletonLine(0.9f, 10.dp)
                }
            }
        }
    }
    when (layout) {
        LayoutClass.Compact -> Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { hero(Modifier); metrics(4); tiles(2); tiles(2); SkeletonCard(lines = 2) }
        LayoutClass.Medium -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 16.dp)) {
            SkeletonLine(0.25f, 22.dp, Modifier.padding(bottom = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    hero(Modifier); metrics(2); metrics(2)
                    SkeletonBox(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(16.dp))
                    SkeletonBox(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(16.dp))
                }
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles(2); tiles(2); SkeletonCard(lines = 2); SkeletonCard(lines = 3); SkeletonCard(lines = 3)
                }
            }
        }
        LayoutClass.Expanded -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp, vertical = 18.dp)) {
            SkeletonLine(0.18f, 22.dp, Modifier.padding(bottom = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        hero(Modifier.width(300.dp).height(300.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { metrics(4); SkeletonCard(lines = 2) }
                    }
                    tiles(4)
                }
                Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { SkeletonCard(lines = 4); SkeletonCard(lines = 2) }
            }
        }
    }
}
