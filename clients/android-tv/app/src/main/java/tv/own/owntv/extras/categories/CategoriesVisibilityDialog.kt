// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.categorylock.CategoryLockPrompts
import tv.own.owntv.features.categorylock.CategoryLockViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupCheck
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StageSearchPill
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@Composable
fun CategoriesVisibilityDialog(
    profileId: Long,
    sourceIds: List<Long>,
    onDismiss: () -> Unit,
    categoryDao: CategoryDao = koinInject(),
    channelDao: ChannelDao = koinInject(),
    customize: CustomizationStore = koinInject(),
    lockVm: CategoryLockViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val categories by categoryDao.observe(sourceIds, MediaType.LIVE).collectAsStateWithLifecycle(emptyList())
    val custom by customize.observe(profileId, MediaType.LIVE).collectAsStateWithLifecycle(tv.own.owntv.core.customize.SectionCustomizations())
    val countsList by channelDao.observeCountsByCategory(sourceIds).collectAsStateWithLifecycle(emptyList())
    val countsMap = remember(countsList) { countsList.associate { it.categoryId to it.itemCount } }

    var searchQuery by remember { mutableStateOf("") }
    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.quick_categories),
        body = stringResource(R.string.quick_categories_hint),
        width = 880.mpx,
        scroll = false,
        buttons = {
            StageButton(
                stringResource(R.string.common_done),
                onClick = onDismiss,
                height = 54.mpx,
                textSize = 18,
            )
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            StageSearchPill(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.categories_search_hint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.mpx),
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.mpx),
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
            ) {
                StageButton(
                    stringResource(R.string.categories_select_all),
                    onClick = {
                        val keys = filteredCategories.map { CustomizeKeys.category(it) }
                        scope.launch { customize.setCategoriesHidden(profileId, MediaType.LIVE, keys, false) }
                    },
                    height = 44.mpx,
                    textSize = 15,
                )
                StageButton(
                    stringResource(R.string.categories_deselect_all),
                    onClick = {
                        val keys = filteredCategories.map { CustomizeKeys.category(it) }
                        scope.launch { customize.setCategoriesHidden(profileId, MediaType.LIVE, keys, true) }
                    },
                    height = 44.mpx,
                    textSize = 15,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.mpx),
                verticalArrangement = Arrangement.spacedBy(6.mpx),
            ) {
                items(filteredCategories, key = { it.id }) { cat ->
                    val catKey = CustomizeKeys.category(cat)
                    val isHidden = catKey in custom.hiddenCategories
                    val isLocked = catKey in custom.lockedCategories
                    val count = countsMap[cat.id]

                    StagePopupOption(
                        title = cat.name,
                        onClick = {
                            scope.launch {
                                customize.setCategoryHidden(profileId, MediaType.LIVE, catKey, !isHidden)
                            }
                        },
                        onLongClick = {
                            lockVm.toggleLock(MediaType.LIVE, LiveKey.Folder(cat.id))
                        },
                        leading = { focused ->
                            StagePopupCheck(on = !isHidden, focused = focused)
                        },
                        trailing = { _ ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (count != null && count > 0) {
                                    Text(
                                        text = count.toString(),
                                        style = stageText(16, 500),
                                        color = StageColors.Dim,
                                        modifier = Modifier.padding(end = 12.mpx),
                                    )
                                }
                                if (isLocked) {
                                    OwnTVIcon(
                                        OwnTVIcon.LOCK,
                                        tint = stageAccent.accent,
                                        modifier = Modifier.size(20.mpx),
                                    )
                                }
                            }
                        },
                    )
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
