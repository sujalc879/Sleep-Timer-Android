package com.sujal.sleeptimer.core.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemBatteryGuideTest {
    @Test fun everySupportedManufacturerGetsItsOwnGuideAndUnknownGetsSafeFallback() {
        val cases = mapOf(
            "SAMSUNG" to "samsung", "Xiaomi" to "xiaomi", "Redmi" to "xiaomi", "POCO" to "xiaomi",
            "OnePlus" to "oneplus", "OPPO" to "oppo", "realme" to "oppo", "vivo" to "vivo",
            "HUAWEI" to "huawei", "HONOR" to "huawei", "motorola" to "motorola", "unknown" to "",
        )
        cases.forEach { (manufacturer, path) ->
            val guide = oemBatteryGuide(manufacturer)
            assertThat(guide.url).isEqualTo("https://dontkillmyapp.com/$path")
            assertThat(guide.instructions).isNotEqualTo(0)
        }
    }
}
