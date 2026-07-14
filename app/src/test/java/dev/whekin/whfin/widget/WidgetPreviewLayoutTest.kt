package dev.whekin.whfin.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetPreviewLayoutTest {
    @Test
    fun `all picker previews can be inflated as RemoteViews`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parent = FrameLayout(context)

        listOf(
            R.layout.widget_preview_1,
            R.layout.widget_preview_2,
            R.layout.widget_preview_3,
            R.layout.widget_preview_4,
        ).forEach { layout ->
            RemoteViews(context.packageName, layout).apply(context, parent)
        }
    }
}
