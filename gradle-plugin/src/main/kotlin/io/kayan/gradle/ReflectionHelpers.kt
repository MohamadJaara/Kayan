package io.kayan.gradle

internal fun getterName(propertyName: String): String =
    "get${propertyName.replaceFirstChar { character -> character.uppercase() }}"
