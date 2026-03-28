package io.kayan.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinTypeNameParserTest {
    @Test
    fun parsesNestedParameterizedNullableTypes() {
        assertEquals(
            "sample.Outer.Inner<kotlin.String, kotlin.collections.List<sample.Value?>>?",
            parseKotlinTypeName("sample.Outer.Inner<String, List<sample.Value?>>?").toString(),
        )
    }

    @Test
    fun preservesFullyQualifiedCollectionTypes() {
        assertEquals(
            "kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.String>>",
            parseKotlinTypeName("kotlin.collections.Map<kotlin.String, kotlin.collections.List<String>>").toString(),
        )
    }
}
