package com.sujal.sleeptimer.core.system

import java.util.Locale

data class OemBatteryGuide(val instructions: Int, val url: String)

fun oemBatteryGuide(manufacturer: String): OemBatteryGuide {
    val name = manufacturer.lowercase(Locale.ROOT)
    val (path, instructions) = when {
        "samsung" in name -> "samsung" to R.string.battery_samsung
        name in setOf("xiaomi", "redmi", "poco") -> "xiaomi" to R.string.battery_xiaomi
        "oneplus" in name -> "oneplus" to R.string.battery_oneplus
        name in setOf("oppo", "realme") -> "oppo" to R.string.battery_oppo
        "vivo" in name -> "vivo" to R.string.battery_vivo
        name in setOf("huawei", "honor") -> "huawei" to R.string.battery_huawei
        "motorola" in name -> "motorola" to R.string.battery_motorola
        else -> "" to R.string.battery_generic
    }
    return OemBatteryGuide(instructions, "https://dontkillmyapp.com/$path")
}
