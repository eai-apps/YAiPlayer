// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import android.annotation.TargetApi
import android.os.Build
import android.service.dreams.DreamService
import android.widget.FrameLayout
import android.widget.ImageView
import coil3.load

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class DaydreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(imageView)

        val item = ScreensaverManifest.FALLBACK_ITEMS.firstOrNull()
        if (item != null) {
            imageView.load(item.imageUrl)
        }

        setContentView(root)
    }
}
