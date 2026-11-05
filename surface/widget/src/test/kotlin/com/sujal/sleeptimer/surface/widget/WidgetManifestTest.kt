package com.sujal.sleeptimer.surface.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class WidgetManifestTest {
    @Test fun widgetMetadataUsesNoPeriodicWakeupsAndProvidesRealInitialLayout() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, DialWidgetReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        val metadata = receiver.loadXmlMetaData(context.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
        metadata.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* skip XML declaration */ }
            val namespace = "http://schemas.android.com/apk/res/android"
            assertThat(it.getAttributeIntValue(namespace, "updatePeriodMillis", -1)).isEqualTo(0)
            val layout = it.getAttributeResourceValue(namespace, "initialLayout", 0)
            assertThat(context.resources.getResourceTypeName(layout)).isEqualTo("layout")
            assertThat(receiver.exported).isFalse()
        }
    }
}
