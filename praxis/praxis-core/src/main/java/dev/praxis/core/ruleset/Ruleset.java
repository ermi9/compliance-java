package dev.praxis.core.ruleset;

import dev.praxis.core.check.CheckOptions;
import java.util.Map;
import java.util.Set;

/**
 * The professor's configuration: which concepts and atomic checks to run, and their options. It
 * <em>selects</em> and <em>tunes</em> — it never contains logic (invariant 6). The submission never
 * contributes to a ruleset; only the professor's file does.
 *
 * @param version          ruleset schema version
 * @param enabledConceptIds concept ids to evaluate
 * @param enabledCheckIds   atomic-check ids to run explicitly (checks referenced by an enabled
 *                          concept are also run, whether or not listed here)
 * @param checkOptions      per-check option maps, keyed by check id
 */
public record Ruleset(
        int version,
        Set<String> enabledConceptIds,
        Set<String> enabledCheckIds,
        Map<String, Map<String, Object>> checkOptions) {

    public Ruleset {
        enabledConceptIds = Set.copyOf(enabledConceptIds);
        enabledCheckIds = Set.copyOf(enabledCheckIds);
        checkOptions = Map.copyOf(checkOptions);
    }

    /** Options for a given check id, or empty if the ruleset specified none. */
    public CheckOptions optionsFor(String checkId) {
        return CheckOptions.of(checkOptions.getOrDefault(checkId, Map.of()));
    }
}
