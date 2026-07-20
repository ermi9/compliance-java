package dev.praxis.core.check;

/**
 * Canonical atomic-check ids, shared between the check implementations (Layer 2, in praxis-checks)
 * and the concept definitions (Layer 3, here in core) so the two never drift out of sync.
 */
public final class CheckIds {

    private CheckIds() {
    }

    /** A non-private, non-final instance field exposes internal state directly. */
    public static final String NO_PUBLIC_MUTABLE_FIELD = "field.no-public-mutable";

    /** A getter returns a mutable internal reference directly instead of a defensive copy. */
    public static final String NO_LEAKED_MUTABLE_INTERNAL = "method.getter-leaks-internal";
}
