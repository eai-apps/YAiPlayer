// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.categorylock.CategoryLockPrompts
import tv.own.owntv.mobile.ui.categorylock.CategoryLockViewModel
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

@Composable
fun CategoriesVisibilitySheet(
    profileId: Long,
    sourceIds: List<Long>,
    onDismiss: () -> Unit,
    categoryDao: CategoryDao = koinInject(),
    channelDao: ChannelDao = koinInject(),
    customize: CustomizationStore = koinInject(),
    lockVm: CategoryLockViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val palette = LocalStagePalette.current
    val accent = stageAccent

    val categories by categoryDao.observe(sourceIds, MediaType.LIVE).collectAsStateWithLifecycle(emptyList())
    val custom by customize.observe(profileId, MediaType.LIVE).collectAsStateWithLifecycle(tv.own.owntv.core.customize.SectionCustomizations())
    val countsList by channelDao.observeCountsByCategory(sourceIds).collectAsStateWithLifecycle(emptyList())
    val countsMap = remember(countsList) { countsList.associate { it.categoryId to it.itemCount } }

    var searchQuery by remember { mutableStateOf("") }
    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.quick_categories),
    ) {
        StageSheetHeader(
            title = stringResource(R.string.quick_categories),
            subtitle = stringResource(R.string.quick_categories_hint),
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            StageSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.categories_search_hint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StageButton(
                    text = stringResource(R.string.categories_select_all),
                    onClick = {
                        val keys = filteredCategories.map { CustomizeKeys.category(it) }
                        scope.launch { customize.setCategoriesHidden(profileId, MediaType.LIVE, keys, false) }
                    },
                    style = StageButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
                StageButton(
                    text = stringResource(R.string.categories_deselect_all),
                    onClick = {
                        val keys = filteredCategories.map { CustomizeKeys.category(it) }
                        scope.launch { customize.setCategoriesHidden(profileId, MediaType.LIVE, keys, true) }
                    },
                    style = StageButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filteredCategories, key = { it.id }) { cat ->
                    val catKey = CustomizeKeys.category(cat)
                    val isHidden = catKey in custom.hiddenCategories
                    val isLocked = catKey in custom.lockedCategories
                    val count = countsMap[cat.id]

                    StageSettingRow(
                        title = cat.name,
                        subtitle = if (count != null && count > 0) count.toString() else null,
                        minHeight = 48.dp,
                        onClick = {
                            scope.launch {
                                customize.setCategoryHidden(profileId, MediaType.LIVE, catKey, !isHidden)
                            }
                        },
                        onLongClick = {
                            lockVm.toggleLock(MediaType.LIVE, LiveKey.Folder(cat.id))
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        lockVm.toggleLock(MediaType.LIVE, LiveKey.Folder(cat.id))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                StageIcon(
                                    icon = MobileIcons.Lock,
                                    size = 18.dp,
                                    tint = if (isLocked) accent.accent else palette.dim.copy(alpha = 0.35f),
                                )
                            }
                            StageSwitch(on = !isHidden)
                        }
                    }
                }
            }
        }
    }

    CategoryLockPrompts(
        type = MediaType.LIVE,
        onAction = { _, _ -> },
        onClosed = { _, _ -> },
    )
}
