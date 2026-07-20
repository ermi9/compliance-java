package dev.praxis.checks;

import dev.praxis.core.check.Check;
import java.util.List;

/** Registry of the atomic checks shipped in this module. */
public final class BuiltInChecks {

    private BuiltInChecks() {
    }

    public static List<Check> all() {
        return List.of(
                new PublicMutableFieldCheck(),
                new LeakyGetterCheck());
    }
}
