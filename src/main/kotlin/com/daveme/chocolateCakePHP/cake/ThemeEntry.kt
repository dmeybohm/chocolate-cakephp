package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.ThemeConfig

data class ThemeEntry(
   var themePath: String,
   var assetPath: String = "webroot",
) {
   companion object {
      @JvmStatic
      fun fromThemeConfig(themeConfig: ThemeConfig): ThemeEntry {
         return ThemeEntry(
            themePath = themeConfig.themePath,
            assetPath = themeConfig.assetPath,
         )
      }
   }

   fun toThemeConfig(): ThemeConfig {
      return ThemeConfig(
         themePath = themePath,
         assetPath = assetPath,
      )
   }
}
