package com.linecorp.intellij.plugins.armeria

import com.intellij.spellchecker.BundledDictionaryProvider

class ArmeriaDictionaryProvider: BundledDictionaryProvider {
    override fun getBundledDictionaries(): Array<String> {
        return arrayOf("/dictionaries/armeria.dic")
    }
}
