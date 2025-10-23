package com.daveme.chocolateCakePHP

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE_NAME = "messages.ChocolateCakePHPBundle"

object ChocolateCakePHPBundle : DynamicBundle(BUNDLE_NAME) {
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
