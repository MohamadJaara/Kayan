package io.kayan.gradle;

import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.TypeName;
import java.util.List;

final class KotlinPoetInterop {
    private KotlinPoetInterop() {
    }

    static ParameterizedTypeName parameterizedTypeName(
        ClassName rawType,
        List<? extends TypeName> typeArguments
    ) {
        return ParameterizedTypeName.Companion.get(rawType, typeArguments);
    }
}
