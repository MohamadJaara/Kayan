package io.kayan

/**
 * Controls how strictly Kayan validates config keys against the local schema.
 *
 * In a multi-module build, several modules can read one shared config file while
 * each module declares only the keys it consumes.
 */
public enum class KayanValidationMode {
    /**
     * Reject any key that is not declared in the local schema.
     *
     * This preserves Kayan's original whole-document validation behavior.
     */
    STRICT,

    /**
     * Validate only keys declared in the local schema and ignore unrelated keys.
     *
     * Declared keys still participate in full validation, including type checks,
     * required-ness, custom override rules, and target-specific resolution.
     */
    SUBSET,
}
