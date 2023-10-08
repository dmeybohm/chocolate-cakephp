package com.daveme.chocolateCakePHP.controller

import java.util.function.Supplier

class NameProvider : Supplier<String> {

    override fun get(): String {
        return "Create View File Icon"
    }
}