package com.propdf.editor.data.repository

import android.content.Context
import android.graphics.Bitmap

class SignatureManager(context: Context) {
    // Placeholder for digital signature implementation
    fun createSignatureBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
