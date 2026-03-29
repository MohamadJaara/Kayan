package io.kayan

/**
 * Controls how strictly Kayan validates config keys against the local schema.
 *
 * This is especially useful in multi-module builds where several modules read
 * the same shared config file but each module declares only the subset of keys
 * it consumes locally.
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
