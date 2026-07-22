package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text

// 统一高度设定为 68dp
val IntegratedFloatingBarHeight = 68.dp
val IntegratedFloatingBarExtraHeight = 4.dp
val IntegratedFloatingBarShadowPadding = 20.dp
val IntegratedFloatingBarToastGap = 12.dp
val IntegratedFloatingBarVisualHeight =
    IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight + IntegratedFloatingBarShadowPadding
// 注意：这个值通常用于外部布局
val IntegratedFloatingBarBottomSpacing = 0.dp

// 全宽沉浸式底栏参数
val IntegratedFloatingBarIconSize = 56.dp
val IntegratedFloatingBarCornerRadius = 20.dp

// --- Hydrogen 核心配色 ---
val HydrogenBg = Color(0xFFF1F1EA)
val HydrogenIndicator = Color(0xFFE2E2D5)
val HydrogenContent = Color(0xFF44473E)

@Composable
fun IntegratedFloatingBar(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isSidebarOpen: Boolean = false,
    navItems: List<String>,
    selectedPageKey: String,
    onMenuClick: () -> Unit,
    onPageClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit,
    actionPanelOpen: Boolean = false,
    onActionPanelToggle: () -> Unit = {},
    onActionPanelClose: () -> Unit = {},
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    navInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()

    val mdBlend = 1.0f
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val navBg = lerp(HydrogenBg, if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, mdBlend)
    val navIndicator = lerp(HydrogenIndicator, MaterialTheme.colorScheme.secondaryContainer, mdBlend)
    val navContent = lerp(HydrogenContent, MaterialTheme.colorScheme.onSurfaceVariant, mdBlend)

    // 沉浸式贴底：背景延伸到底部导航手势区，图标内容上移到手势线之上
    val navElevation = 0.dp
    val navHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val barTotalHeight = navHeight + navInset

    val normalizedNavItems = navItems.distinct().filter {
        it == HomeEntryKey.TODAY || it == HomeEntryKey.ALL || it == HomeEntryKey.NOTE
    }

    val isMenuSelected = isSidebarOpen
    val isTabHighlightEnabled = !isSidebarOpen
    val menuIcon = painterResource(R.drawable.floatingbar_menu)
    val todayIcon = painterResource(R.drawable.floatingbar_today)
    val allIcon = painterResource(R.drawable.floatingbar_all)
    val quickMemoIcon = painterResource(R.drawable.ic_stat_quickmemo)

    fun painterIconForPageKey(key: String): Painter? = when (key) {
        HomeEntryKey.TODAY -> todayIcon
        HomeEntryKey.ALL -> allIcon
        HomeEntryKey.NOTE -> quickMemoIcon
        else -> null
    }

    fun vectorIconForPageKey(key: String): ImageVector? = when (key) {
        else -> null
    }

    val effectiveSelectedKey = if (selectedPageKey in normalizedNavItems) {
        selectedPageKey
    } else {
        normalizedNavItems.firstOrNull() ?: HomeEntryKey.TODAY
    }
    val currentTabClick = { onPageClick(effectiveSelectedKey) }

    @Composable
    fun FloatingContainer(
        modifier: Modifier,
        shape: androidx.compose.ui.graphics.Shape,
        containerColor: Color,
        elevation: Dp,
        content: @Composable () -> Unit
    ) {
        if (backgroundMode) {
            AppCard(
                modifier = modifier,
                shape = shape,
                containerColor = containerColor,
                shadowElevation = 0.dp,
            ) {
                content()
            }
        } else {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                modifier = modifier
            ) {
                content()
            }
        }
    }

    // 全宽沉浸式底栏：贴底、背景延伸到导航手势区，顶部圆角
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                visible = actionPanelOpen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                ActionExpandPanel(
                    onSearchClick = onSearchClick,
                    onImageClick = onImageClick,
                    onEditClick = onEditClick,
                    onCloseClick = onActionPanelClose
                )
            }
            FloatingContainer(
            modifier = Modifier
                .fillMaxWidth()
                .height(barTotalHeight),
            shape = RoundedCornerShape(
                topStart = IntegratedFloatingBarCornerRadius,
                topEnd = IntegratedFloatingBarCornerRadius,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            containerColor = navBg,
            elevation = navElevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 8.dp, bottom = navInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (isExpanded) {
                    // 折叠态：仅显示菜单按钮（左对齐）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        HydrogenNavIcon(
                            icon = menuIcon,
                            isSelected = isMenuSelected,
                            indicatorColor = navIndicator,
                            contentColor = navContent,
                            onClick = {
                                onExpandedChange(false)
                                onMenuClick()
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        HydrogenNavIcon(
                            icon = menuIcon,
                            isSelected = isMenuSelected,
                            indicatorColor = navIndicator,
                            contentColor = navContent,
                            onClick = onMenuClick
                        )
                    }
                    normalizedNavItems.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            HydrogenNavIcon(
                                icon = painterIconForPageKey(key),
                                vectorIcon = vectorIconForPageKey(key),
                                isSelected = isTabHighlightEnabled && effectiveSelectedKey == key,
                                indicatorColor = navIndicator,
                                contentColor = navContent,
                                onClick = { onPageClick(key) }
                            )
                        }
                    }
                    // 加号操作按钮：浅蓝主色，点击展开/收起操作面板
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingActionIcon(
                            onClick = onActionPanelToggle
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun HydrogenNavIcon(
    icon: Painter?,
    vectorIcon: ImageVector? = null,
    isSelected: Boolean,
    indicatorColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .size(IntegratedFloatingBarIconSize)
            .clip(CircleShape)
            .clickable { haptics.click(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(indicatorColor, CircleShape)
            )
        }
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        } else if (vectorIcon != null) {
            Icon(
                imageVector = vectorIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun ActionExpandPanel(
    onSearchClick: () -> Unit,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val panelBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = panelBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionMenuItem(icon = Icons.Default.Search, label = "搜索", onClick = onSearchClick, modifier = Modifier.weight(1f))
            ActionMenuItem(icon = Icons.Default.Image, label = "图片", onClick = onImageClick, modifier = Modifier.weight(1f))
            ActionMenuItem(icon = Icons.Default.Edit, label = "编辑", onClick = onEditClick, modifier = Modifier.weight(1f))
            ActionMenuItem(icon = Icons.Default.Close, label = "关闭", onClick = onCloseClick, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionMenuItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAppHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable { haptics.click(); onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FloatingActionIcon(onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { haptics.click(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "操作菜单",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(26.dp)
        )
    }
}
