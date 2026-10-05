package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.home.KrispyHomeBackdropLoader
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A local sharp backdrop; the global backdrop preference and other screens are untouched. */
class KrispyDetailsBackdropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs), KoinComponent {
	private val loader by inject<KrispyHomeBackdropLoader>()
	private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
	private var loadJob: Job? = null
	private var loadedItem: BaseItemDto? = null

	init {
		setBackgroundColor(Color.BLACK)
		isFocusable = false
		addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		addView(Fade(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
	}

	fun showItem(item: BaseItemDto?) {
		if (item == loadedItem) return
		loadedItem = item
		loadJob?.cancel()
		image.setImageDrawable(null)
		if (item == null) return
		doOnAttach {
			if (loadedItem != item) return@doOnAttach
			loadJob = findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				val artwork = loader.load(item)
				image.setImageBitmap(artwork?.asAndroidBitmap())
			}
		}
	}

	private class Fade(context: Context) : View(context) {
		private val paint = Paint()
		private var vertical: Shader? = null
		private var horizontal: Shader? = null

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			vertical = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x20000000, 0x20000000, 0xA6000000.toInt(), 0xE6000000.toInt(), Color.BLACK), floatArrayOf(0f, 0.32f, 0.55f, 0.83f, 1f), Shader.TileMode.CLAMP)
			horizontal = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0x40000000, 0x20000000, Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 0.75f), Shader.TileMode.CLAMP)
		}

		override fun onDraw(canvas: Canvas) {
			paint.shader = vertical
			canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
			paint.shader = horizontal
			canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
		}
	}
}
