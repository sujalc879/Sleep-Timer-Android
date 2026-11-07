package com.sujal.sleeptimer.surface.widget

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.composeForPreview
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewRenderingTest {
    @Test
    @Config(qualifiers = "notnight-mdpi")
    fun dialLight() = render(DialWidget(), "dial-light", 180, 180)

    @Test
    @Config(qualifiers = "night-mdpi")
    fun dialDark() = render(DialWidget(), "dial-dark", 180, 180)

    @Test
    @Config(qualifiers = "notnight-mdpi")
    fun presetsLight() = render(PresetsWidget(), "presets-light", 320, 80)

    @Test
    @Config(qualifiers = "night-mdpi")
    fun presetsDark() = render(PresetsWidget(), "presets-dark", 320, 80)

    private fun render(widget: GlanceAppWidget, name: String, width: Int, height: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val remote = widget.composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        val view = remote.apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        assertThat(texts(view)).contains("30")
        if (widget is PresetsWidget) {
            assertThat(texts(view)).containsAtLeast("15", "45", "60")
        } else {
            assertThat(texts(view)).contains("Tap to start")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val referenceId = if (widget is PresetsWidget) {
            R.drawable.widget_preview_presets
        } else {
            R.drawable.widget_preview_dial
        }
        val reference = BitmapFactory.decodeResource(context.resources, referenceId)
        assertThat(bitmap.sameAs(reference)).isTrue()
        val output = File("build/outputs/widget-previews/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
}
