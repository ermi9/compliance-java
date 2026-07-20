package dev.praxis.core.check;

import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of running one atomic check over the whole project: an aggregate {@link TriState}
 * (used by Layer-3 composition) plus the evidence {@link Finding}s worth surfacing.
 *
 * <p>{@code overall} follows the atomic-check aggregation rule (AND-fold across subjects): a proven
 * {@code VIOLATION} on any resolved subject dominates, else any {@code UNDETERMINED} wins, else
 * {@code SATISFIED}. Findings are stored sorted for deterministic output (invariant 4).
 */
public record CheckReport(String checkId, TriState overall, List<Finding> findings) {

    public CheckReport {
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort(null); // Finding is Comparable with a total, deterministic order
        findings = List.copyOf(sorted);
    }

    /**
     * Builds a report from the per-subject verdicts and the evidence findings. {@code overall} is
     * the AND-fold of {@code subjectStates} (empty ⇒ {@code SATISFIED}).
     */
    public static CheckReport of(String checkId, List<TriState> subjectStates, List<Finding> findings) {
        return new CheckReport(checkId, TriState.allOf(subjectStates), findings);
    }

    /** A report for a check that found no subjects to evaluate. */
    public static CheckReport satisfied(String checkId) {
        return new CheckReport(checkId, TriState.SATISFIED, List.of());
    }
}
