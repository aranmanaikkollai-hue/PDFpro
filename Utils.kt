package com.propdf.editor.utils

import android.content.Context

fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

fun pxToDp(context: Context, px: Int): Int {
    return (px / context.resources.displayMetrics.density).toInt()
}
