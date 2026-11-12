package com.sujal.sleeptimer.feature.settings

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class DiagnosticSharingTest {
    @Test fun reportAlwaysOpensChooserWithoutPreselectingARecipient() {
        val chooser = diagnosticsShareIntent("local timer events")
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION") // Exercise the same framework contract on API 26 and 35.
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("text/plain")
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("local timer events")
        assertThat(send.component).isNull()
        assertThat(send.`package`).isNull()
        assertThat(send.data).isNull()
        assertThat(send.flags).isEqualTo(0)
    }
}
