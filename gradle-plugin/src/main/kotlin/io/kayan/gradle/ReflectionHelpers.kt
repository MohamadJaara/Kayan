package io.kayan.gradle

import java.lang.reflect.Method

internal fun getterName(propertyName: String): String =
    "get${propertyName.replaceFirstChar { character -> character.uppercase() }}"

internal fun Any.invokeNoArgOrNull(methodName: String): Any? =
    methodOrNull(methodName, 0)?.let { method ->
        runCatching {
            method.invoke(this)
        }.getOrNull()
    }

internal fun Any.readStringPropertyOrNull(getterName: String): String? =
    invokeNoArgOrNull(getterName) as? String

internal fun Any.methodOrNull(
    methodName: String,
    parameterCount: Int,
): Method? = javaClass.methods.firstOrNull { method ->
    method.name == methodName && method.parameterCount == parameterCount
}
