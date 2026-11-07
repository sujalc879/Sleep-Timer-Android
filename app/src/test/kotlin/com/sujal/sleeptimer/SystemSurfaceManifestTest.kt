package com.sujal.sleeptimer

import android.app.Application
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.timer.SurfaceIntents
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class SystemSurfaceManifestTest {
    @Test fun staticShortcutIntentsResolveTheCurrentVariantAndKnownActions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val actions = mutableListOf<String>()
        context.resources.getXml(R.xml.shortcuts).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                    val intent = Intent.parseIntent(context.resources, parser, Xml.asAttributeSet(parser))
                    assertThat(intent.component?.packageName).isEqualTo(context.packageName)
                    assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
                    actions.add(intent.action!!)
                }
            }
        }
        assertThat(actions).containsExactly(SurfaceIntents.START_30, SurfaceIntents.START_60, SurfaceIntents.CUSTOM)
    }

    @Test fun launcherIconIsAdaptiveAndIncludesThemedMonochromeOnSupportedAndroid() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertThat(icon.foreground).isNotNull()
        assertThat(icon.background).isNotNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) assertThat(icon.monochrome).isNotNull()
    }
}
