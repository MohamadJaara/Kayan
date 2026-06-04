package io.kayan.gradle

import io.kayan.ConfigValueKind

/**
 * Builder used inside `kayan { schema { ... } }` to declare config entries.
 *
 * Each function maps a source key to a generated Kotlin property. The shared
 * flags behave the same across entry types: `required` forces every final
 * flavor result to contain a value, `nullable` allows explicit `null`, and
 * `preventOverride` blocks the custom override file from replacing the base
 * value.
 */
@Suppress("TooManyFunctions")
public class KayanSchemaBuilder internal constructor() {
    internal val entries: MutableList<KayanSchemaEntrySpec> = mutableListOf()
    private val includedJsonKeys: LinkedHashSet<String> = linkedSetOf()
    private var includeAllFromRoot: Boolean = false
    private var includeOnlyMode: Boolean = false

    /** Includes one shared schema entry from `kayanRoot { schema { ... } }`. */
    public fun include(jsonKey: String) {
        includedJsonKeys += jsonKey
    }

    /** Includes every shared schema entry from `kayanRoot { schema { ... } }`. */
    public fun includeAll() {
        includeAllFromRoot = true
    }

    /** Adds a string schema entry. */
    public fun string(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a boolean schema entry. */
    public fun boolean(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.BOOLEAN,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds an integer schema entry. */
    public fun int(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.INT,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a long schema entry. */
    public fun long(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.LONG,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a double schema entry. */
    public fun double(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.DOUBLE,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string-to-string map schema entry. */
    public fun stringMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_MAP,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string list schema entry. */
    public fun stringList(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string-to-string-list map schema entry. */
    public fun stringListMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST_MAP,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds an enum-backed schema entry using [enumTypeName] as the generated enum type name. */
    public fun enumValue(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.ENUM,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
            preventOverride = preventOverride,
        )
    }

    /** Alias for [enumValue] for DSL ergonomics. */
    public fun enum(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        enumValue(
            jsonKey = jsonKey,
            propertyName = propertyName,
            enumTypeName = enumTypeName,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a custom schema entry rendered through the adapter identified by [adapter]. */
    public fun custom(
        jsonKey: String,
        propertyName: String,
        rawKind: ConfigValueKind,
        adapter: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = rawKind,
            required = required,
            nullable = nullable,
            adapterClassName = adapter,
            preventOverride = preventOverride,
        )
    }

    @Suppress("LongParameterList")
    private fun add(
        jsonKey: String,
        propertyName: String,
        kind: ConfigValueKind,
        required: Boolean,
        nullable: Boolean,
        enumTypeName: String? = null,
        adapterClassName: String? = null,
        preventOverride: Boolean = false,
    ) {
        if (includeOnlyMode) {
            throw PluginConfigurationError.LocalSchemaEntriesNotSupportedWithRootInheritance.toGradleException()
        }

        entries += KayanSchemaEntrySpec(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = kind,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
            adapterClassName = adapterClassName,
            preventOverride = preventOverride,
        )
    }

    internal fun enableIncludeOnlyMode() {
        includeOnlyMode = true
    }

    internal fun hasLocalEntries(): Boolean = entries.isNotEmpty()

    internal fun hasRootIncludes(): Boolean = includeAllFromRoot || includedJsonKeys.isNotEmpty()

    internal fun includesAll(): Boolean = includeAllFromRoot

    internal fun includedJsonKeys(): Set<String> = includedJsonKeys

    internal fun serializedLocalEntries(): List<String> = entries.map(KayanSchemaEntrySpec::serialize)
}
