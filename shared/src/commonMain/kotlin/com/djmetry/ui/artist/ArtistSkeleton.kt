package com.djmetry.ui.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.djmetry.ui.components.SkeletonBox
import com.djmetry.ui.components.SkeletonCard
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.layout.LayoutClass

/** Скелетон карточки артиста — та же раскладка, что у готового экрана (постер, действия, секции). */
@Composable
internal fun ArtistSkeleton(layout: LayoutClass) {
    when (layout) {
        LayoutClass.Compact -> Column(Modifier.fillMaxSize()) {
            SkeletonBox(Modifier.fillMaxWidth().height(470.dp), RectangleShape)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionsSkeleton()
                SkeletonCard(lines = 2)
                SkeletonCard(lines = 3)
            }
        }
        LayoutClass.Medium -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 16.dp)) {
            SkeletonLine(0.12f, 18.dp, Modifier.padding(bottom = 20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(440.dp), RoundedCornerShape(28.dp))
                    ActionsSkeleton()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SkeletonCard(lines = 2); SkeletonCard(lines = 5); SkeletonCard(lines = 3)
                }
            }
        }
        LayoutClass.Expanded -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SkeletonLine(0.08f, 18.dp)
            SkeletonBox(Modifier.fillMaxWidth().height(300.dp), RoundedCornerShape(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SkeletonCard(lines = 5, modifier = Modifier.weight(1f))
                SkeletonCard(lines = 5, modifier = Modifier.weight(1f))
                Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { SkeletonCard(lines = 2); SkeletonCard(lines = 2) }
            }
        }
    }
}

@Composable
private fun ActionsSkeleton() {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(Modifier.weight(1.3f).fillMaxHeight(), RoundedCornerShape(16.dp))
        SkeletonBox(Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(16.dp))
    }
}
