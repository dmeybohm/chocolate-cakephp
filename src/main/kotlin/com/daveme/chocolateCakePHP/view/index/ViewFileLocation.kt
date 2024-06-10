package com.daveme.chocolateCakePHP.view.index

data class ViewFileLocation(
    val filename: String,
    val prefixPath: String,
    val viewType: ViewType
) {
    enum class ViewType {
        VIEW,
        ELEMENT
    }
}