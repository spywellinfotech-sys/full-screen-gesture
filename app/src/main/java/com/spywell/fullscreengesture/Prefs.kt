package com.spywell.fullscreengesture

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val LEFT = "left"
    const val RIGHT = "right"
    const val BOTTOM = "bottom"
    const val HAPTIC = "haptic"
    const val EDGE_DP = "edge_dp"
    const val BOTTOM_DP = "bottom_dp"

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("gesture", Context.MODE_PRIVATE)

    fun bool(c: Context, key: String) = sp(c).getBoolean(key, true)
    fun edgeDp(c: Context) = sp(c).getInt(EDGE_DP, 24)
    fun bottomDp(c: Context) = sp(c).getInt(BOTTOM_DP, 24)
}
