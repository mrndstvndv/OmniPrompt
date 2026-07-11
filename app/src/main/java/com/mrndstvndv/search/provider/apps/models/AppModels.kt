package com.mrndstvndv.search.provider.apps.models

data class AppInfo(
    val packageName: String,
    val label: String,
    val userSerialNumber: Long = 0L,
) {
    val labelLower: String = label.lowercase()
    val packageNameLower: String = packageName.lowercase()
}
