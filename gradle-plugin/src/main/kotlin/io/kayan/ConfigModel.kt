package io.kayan

public enum class ConfigValueKind {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    DOUBLE,
    STRING_MAP,
    STRING_LIST_MAP,
    STRING_LIST,
    ENUM,
}

public sealed interface ConfigValue {
    public val kind: ConfigValueKind

    public data class StringValue(val value: String) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.STRING
    }

    public data class BooleanValue(val value: Boolean) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.BOOLEAN
    }

    public data class IntValue(val value: Int) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.INT
    }

    public data class LongValue(val value: Long) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.LONG
    }

    public data class DoubleValue(val value: Double) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.DOUBLE
    }

    public data class StringMapValue(val value: Map<String, String>) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.STRING_MAP
    }

    public data class StringListMapValue(val value: Map<String, List<String>>) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.STRING_LIST_MAP
    }

    public data class StringListValue(val value: List<String>) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.STRING_LIST
    }

    public data class EnumValue(val value: String) : ConfigValue {
        override val kind: ConfigValueKind = ConfigValueKind.ENUM
    }

    public data class NullValue(
        override val kind: ConfigValueKind,
    ) : ConfigValue
}

public data class ConfigDefinition(
    val jsonKey: String,
    val propertyName: String,
    val kind: ConfigValueKind,
    val required: Boolean = false,
    val nullable: Boolean = false,
    val enumTypeName: String? = null,
    val adapterClassName: String? = null,
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

public class ConfigSchema(
    entries: List<ConfigDefinition>,
) {
    public val entries: List<ConfigDefinition> = entries.toList()

    private val byJsonKey: Map<String, ConfigDefinition> = this.entries.associateBy(ConfigDefinition::jsonKey)

    init {
        require(this.entries.isNotEmpty()) { "Config schema must contain at least one definition." }
        require(this.entries.size == byJsonKey.size) { "Config schema contains duplicate jsonKey values." }
        require(
            this.entries.size == this.entries.map(ConfigDefinition::propertyName).toSet().size
        ) { "Config schema contains duplicate propertyName values." }
    }

    public fun definitionFor(jsonKey: String): ConfigDefinition? = byJsonKey[jsonKey]
}

public data class ConfigSection(
    val values: Map<ConfigDefinition, ConfigValue>,
) {
    public operator fun get(definition: ConfigDefinition): ConfigValue? = values[definition]
}

public data class AppConfigFile(
    val defaults: ConfigSection,
    val flavors: Map<String, ConfigSection>,
)

public data class ResolvedFlavorConfig(
    val flavorName: String,
    val values: Map<ConfigDefinition, ConfigValue?>,
) {
    public operator fun get(definition: ConfigDefinition): ConfigValue? = values[definition]
}

public data class ResolvedConfigsByFlavor(
    val flavors: Map<String, ResolvedFlavorConfig>,
)

public class ConfigValidationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

public interface ConfigResolver {
    public fun parse(
        configJson: String,
        schema: ConfigSchema,
    ): AppConfigFile

    public fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String? = null,
    ): ResolvedConfigsByFlavor
}
