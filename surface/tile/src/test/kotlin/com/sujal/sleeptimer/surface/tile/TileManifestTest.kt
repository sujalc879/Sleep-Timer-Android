package com.sujal.sleeptimer.surface.tile

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.system.TileMembership
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class TileManifestTest {
    @Test fun exportedBindingIsProtectedByAndroidTilePermission() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, SleepTimerTile::class.java),
            PackageManager.GET_META_DATA,
        )
        assertThat(service.exported).isTrue()
        assertThat(service.permission).isEqualTo("android.permission.BIND_QUICK_SETTINGS_TILE")
        assertThat(service.metaData.getBoolean("android.service.quicksettings.ACTIVE_TILE")).isTrue()
    }

    @Test fun addedAndRemovedMembershipSurvivesControllerRecreation() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TileMembership(context).added = true
        assertThat(TileMembership(context).added).isTrue()
        TileMembership(context).added = false
        assertThat(TileMembership(context).added).isFalse()
    }
}
