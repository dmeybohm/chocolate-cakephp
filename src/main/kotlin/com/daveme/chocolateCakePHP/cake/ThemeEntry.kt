package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.ThemeConfig

data class ThemeEntry(
   var pluginPath: String,
   var assetPath: String = "webroot",
) {
   companion object {
      @JvmStatic
      fun fromThemeConfig(themeConfig: ThemeConfig): ThemeEntry {
         return ThemeEntry(
            pluginPath = themeConfig.pluginPath,
            assetPath = themeConfig.assetPath,
         )
      }
   }

   fun toThemeConfig(): ThemeConfig {
      return ThemeConfig(
         pluginPath = pluginPath,
         assetPath = assetPath,
      )
   }
}
