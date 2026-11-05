package tv.own.owntv.mobile.ui.screens.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageTool

/**
 * Live TV's two views as one visible switch: [List | Guide], the active one lit. Replaces the old
 * single "Guide view" icon, which toggled silently and was remembered, so the guide seemed to stick.
 */
@Composable
fun LiveViewSwitch(guide: Boolean, onList: () -> Unit, onGuide: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StageTool(
            onClick = onList,
            icon = MobileIcons.LiveTv,
            value = stringResource(R.string.settings_view_list),
            on = !guide,
        )
        StageTool(
            onClick = onGuide,
            icon = MobileIcons.Guide,
            value = stringResource(R.string.settings_seg_guide),
            on = guide,
        )
    }
}
