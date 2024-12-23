package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.PluginConfig

interface PluginConfigListener {
    fun actionPerformed(config: PluginConfig)
}