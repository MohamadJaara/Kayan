package io.kayan.gradle;

import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.TypeName;
import java.util.Arrays;
import java.util.List;

/**
 * Small KotlinPoet helpers for adapter implementations that need to declare
 * generated Kayan property types.
 */
public final class KayanTypeNames {
    private KayanTypeNames() {
    }

    /**
     * Resolves a fully-qualified or nested Kotlin type name into a KotlinPoet {@link ClassName}.
     */
    public static ClassName bestGuess(String canonicalName) {
        return ClassName.bestGuess(canonicalName);
    }

    /**
     * Creates a parameterized KotlinPoet type from a raw type and vararg type arguments.
     */
    public static ParameterizedTypeName parameterized(
        ClassName rawType,
        TypeName... typeArguments
    ) {
        return parameterized(rawType, Arrays.asList(typeArguments));
    }

    /**
     * Creates a parameterized KotlinPoet type from a raw type and list of type arguments.
     */
    public static ParameterizedTypeName parameterized(
        ClassName rawType,
        List<? extends TypeName> typeArguments
    ) {
        return ParameterizedTypeName.Companion.get(rawType, typeArguments);
    }
}
