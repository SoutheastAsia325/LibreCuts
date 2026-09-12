package com.tharunbirla.librecuts.utils

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * 毛玻璃（glassmorphism）能力探测与常量。
 *
 * 技术约束：真实背景模糊依赖 [RenderEffect]，仅 **API 31+** 可用，而本项目 minSdk 为 26。
 * 因此：
 *  - API 31+：[View.applyGlassBlur] 得到真·磨砂；
 *  - API 26-30：静默跳过，由 drawable 的「半透明填充 + 1dp 细描边」模拟毛玻璃层次。
 */
object GlassUi {

    /** 默认模糊半径（dp）。 */
    const val DEFAULT_BLUR_RADIUS_DP = 40f

    /** 当前设备是否支持真·模糊。 */
    val isRealBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 给背景层加真实模糊；API 31 以下静默跳过（由 drawable 兜底）。
 *
 * 用法要点：模糊应作用于**装饰性背景层**（例如首页底图 / 光晕），而不是玻璃面板本身，
 * 这样面板上的半透明填充才会呈现出磨砂质感。
 */
fun View.applyGlassBlur(radiusDp: Float = GlassUi.DEFAULT_BLUR_RADIUS_DP) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val radiusPx = radiusDp * resources.displayMetrics.density
    setRenderEffect(
        RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    )
}

/** 移除模糊；API 31 以下为 no-op。 */
fun View.clearGlassBlur() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    setRenderEffect(null)
}
