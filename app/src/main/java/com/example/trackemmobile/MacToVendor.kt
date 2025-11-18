package com.example.trackemmobile

object MacToVendor {
    private val ouiMap = mapOf(
        "00:03:7F" to "Apple, Inc.",
        "00:05:02" to "Apple, Inc.",
        "00:10:FA" to "Apple, Inc.",
        "00:A0:40" to "Apple, Inc.",
        "40:A3:CC" to "Apple, Inc.",
        "F8:E0:79" to "Apple, Inc.",
        "BC:3B:AF" to "Apple, Inc.",
        "8C:2D:AA" to "Apple, Inc.",
        "D8:9E:3F" to "Google, Inc.",
        "A4:77:33" to "Google, Inc.",
        "3C:5A:B4" to "Google, Inc.",
        "00:1A:11" to "Google, Inc.",
        "E0:F8:47" to "Samsung Electronics Co.,Ltd",
        "BC:F5:AC" to "Samsung Electronics Co.,Ltd",
        "88:C6:26" to "Samsung Electronics Co.,Ltd",
        "20:54:76" to "Samsung Electronics Co.,Ltd"
    )

    fun getVendor(macOui: String): String? {
        return ouiMap[macOui.uppercase()]
    }
}
