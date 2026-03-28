package io.kayan

/**
 * Raw value kinds understood by Kayan during parsing and validation.
 *
 * This describes the stored shape of a value, independent of whether the entry
 * is required, nullable, or backed by a custom adapter.
 */
public enum class ConfigValueKind {
    /** A string value. */
    STRING,

    /** A boolean value. */
    BOOLEAN,

    /** A 32-bit integer value. */
    INT,

    /** A 64-bit integer value. */
    LONG,

    /** A floating-point value. */
    DOUBLE,

    /** A map of string keys to string values. */
    STRING_MAP,

    /** A map of string keys to string lists. */
    STRING_LIST_MAP,

    /** A list of strings. */
    STRING_LIST,

    /** An enum name serialized as a string. */
    ENUM,
}

/**
 * Parsed representation of a config value before it is rendered into generated source.
 *
 * Resolver APIs use this hierarchy in parsed and merged models. Nullable entries
 * are represented explicitly with [NullValue] instead of overloading Kotlin null.
 */
public sealed interface ConfigValue {
    /** The schema kind represented by this value. */
    public val kind: ConfigValueKind

    /**
     * A string config value.
     *
     * @property value The decoded string value.
     */
    public data class StringValue(val value: String) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.STRING
    }

    /**
     * A boolean config value.
     *
     * @property value The decoded boolean value.
     */
    public data class BooleanValue(val value: Boolean) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.BOOLEAN
    }

    /**
     * An integer config value.
     *
     * @property value The decoded integer value.
     */
    public data class IntValue(val value: Int) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.INT
    }

    /**
     * A long config value.
     *
     * @property value The decoded long value.
     */
    public data class LongValue(val value: Long) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.LONG
    }

    /**
     * A double config value.
     *
     * @property value The decoded double value.
     */
    public data class DoubleValue(val value: Double) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.DOUBLE
    }

    /**
     * A string-to-string map config value.
     *
     * @property value The decoded map value.
     */
    public data class StringMapValue(val value: Map<String, String>) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.STRING_MAP
    }

    /**
     * A string-to-string-list map config value.
     *
     * @property value The decoded map value.
     */
    public data class StringListMapValue(val value: Map<String, List<String>>) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.STRING_LIST_MAP
    }

    /**
     * A string list config value.
     *
     * @property value The decoded list value.
     */
    public data class StringListValue(val value: List<String>) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.STRING_LIST
    }

    /**
     * An enum config value represented by its normalized constant name.
     *
     * @property value The decoded enum constant name.
     */
    public data class EnumValue(val value: String) : ConfigValue {
        /** The schema kind represented by this value. */
        override val kind: ConfigValueKind = ConfigValueKind.ENUM
    }

    /**
     * A null config value associated with a specific schema kind.
     *
     * @property kind The schema kind of the nullable value.
     */
    public data class NullValue(
        override val kind: ConfigValueKind,
    ) : ConfigValue
}

/**
 * Declares how one config key should be validated and exposed in generated Kotlin.
 *
 * Kayan reads [jsonKey] from the source file, validates it against [kind], and
 * emits the final resolved value as [propertyName]. The constraint flags shape
 * layering behavior: [required] forces every flavor to end up with a value,
 * [nullable] allows explicit `null`, and [preventOverride] blocks the custom
 * override file from replacing the base value.
 *
 * @property jsonKey The key expected in the source config file.
 * @property propertyName The generated Kotlin property name.
 * @property kind The raw value kind expected for this entry.
 * @property required Whether every final flavor result must contain a value.
 * @property nullable Whether the entry may be explicitly set to `null`.
 * @property enumTypeName The generated enum type name used for values exposed as normalized constant names.
 * @property adapterClassName The adapter class used to convert custom build-time values.
 * @property preventOverride Whether the custom override file may replace the base value.
 */
public data class ConfigDefinition(
    val jsonKey: String,
    val propertyName: String,
    val kind: ConfigValueKind,
    val required: Boolean = false,
    val nullable: Boolean = false,
    val enumTypeName: String? = null,
    val adapterClassName: String? = null,
    val preventOverride: Boolean = false,
) {
    init {
        require(jsonKey.isNotBlank()) { "Config definition jsonKey must not be blank." }
        require(propertyName.isNotBlank()) { "Config definition propertyName must not be blank." }
        require(!required || !nullable) {
            "Config definition '$jsonKey' cannot be both required and nullable."
        }
        require(kind == ConfigValueKind.ENUM || enumTypeName == null) {
            "Config definition '$jsonKey' can only set enumTypeName for enum values."
        }
        require(kind != ConfigValueKind.ENUM || !enumTypeName.isNullOrBlank()) {
            "Config definition '$jsonKey' must declare enumTypeName for enum values."
        }
        require(adapterClassName == null || adapterClassName.isNotBlank()) {
            "Config definition adapterClassName must not be blank."
        }
    }
}

/**
 * Validated schema used for parsing, resolution, schema export, and source generation.
 *
 * Construction fails fast when the schema is empty or contains duplicate source
 * keys or generated property names.
 *
 * @property entries The schema entries, preserved in declaration order.
 */
public class ConfigSchema(
    entries: List<ConfigDefinition>,
) {
    /** The definitions included in this schema, preserved in declaration order. */
    public val entries: List<ConfigDefinition> = entries.toList()

    private val byJsonKey: Map<String, ConfigDefinition> = this.entries.associateBy(ConfigDefinition::jsonKey)

    init {
        require(this.entries.isNotEmpty()) { "Config schema must contain at least one definition." }
        require(this.entries.size == byJsonKey.size) { "Config schema contains duplicate jsonKey values." }
        require(
            this.entries.size == this.entries.map(ConfigDefinition::propertyName).toSet().size
        ) { "Config schema contains duplicate propertyName values." }
    }

    /** Returns the declared schema entry for [jsonKey], or `null` when the key is unknown. */
    public fun definitionFor(jsonKey: String): ConfigDefinition? = byJsonKey[jsonKey]
}

/**
 * A single parsed section from a config document, either defaults or one flavor block.
 *
 * The map is keyed by [ConfigDefinition] instead of raw strings so callers can
 * keep working with normalized schema metadata after parsing. Sections may also
 * contain target-specific overrides keyed by target name.
 *
 * @property values The parsed values keyed by schema definition.
 * @property targets Target-specific overrides nested under this section.
 */
public data class ConfigSection(
    val values: Map<ConfigDefinition, ConfigValue>,
    val targets: Map<String, ConfigSection> = emptyMap(),
) {
    /** Returns the value stored for [definition], or `null` if it is absent. */
    public operator fun get(definition: ConfigDefinition): ConfigValue? = values[definition]
}

/**
 * Parsed contents of one Kayan config file before any external override file is merged in.
 *
 * @property defaults The top-level default values.
 * @property flavors The flavor-specific overrides keyed by flavor name.
 */
public data class AppConfigFile(
    val defaults: ConfigSection,
    val flavors: Map<String, ConfigSection>,
)

/**
 * Final values for one flavor after defaults, optional overrides, and optional
 * target-specific overlays have been layered.
 *
 * @property flavorName The resolved flavor name.
 * @property targetName The resolved target name when target overlays were applied.
 * @property values The resolved values for each schema definition. Nullable entries may contain `null`.
 */
public data class ResolvedFlavorConfig(
    val flavorName: String,
    val targetName: String? = null,
    val values: Map<ConfigDefinition, ConfigValue?>,
) {
    /**
     * Returns the resolved value for [definition].
     *
     * Kotlin `null` means the key was never resolved into [values] at all, which matches the
     * missing-key path in `resolvedValue` and the required-key check in
     * `requireResolvedRequiredKeysEither`.
     *
     * An explicit config `null` is represented as [ConfigValue.NullValue], meaning the key is
     * present in [values] with a null value rather than absent from the map.
     */
    public operator fun get(definition: ConfigDefinition): ConfigValue? = values[definition]
}

/**
 * Final resolution result for every flavor declared in the base config file.
 *
 * @property flavors The resolved configs for each flavor.
 */
public data class ResolvedConfigsByFlavor(
    val flavors: Map<String, ResolvedFlavorConfig>,
)

/** Public exception type used when resolver operations fail validation or parsing. */
public class ConfigValidationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Parses config documents and resolves them into typed, flavor-specific results. */
public interface ConfigResolver {
    /** Parses a single config document into defaults and flavor sections without applying custom overrides. */
    public fun parse(
        configJson: String,
        schema: ConfigSchema,
    ): AppConfigFile

    /** Resolves the base document and optional custom override document into final values by flavor. */
    public fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String? = null,
    ): ResolvedConfigsByFlavor
}
