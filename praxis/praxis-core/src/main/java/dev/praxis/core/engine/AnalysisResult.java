package dev.praxis.core.engine;

import dev.praxis.core.check.CheckReport;
import dev.praxis.core.concept.ConceptResult;
import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.List;

/**
 * The engine's output for one submission: the atomic-check reports, the evaluated concept verdicts,
 * every finding (unioned and sorted), the files that failed to parse, and the overall verdict.
 * All lists are deterministically ordered (invariant 4).
 */
public record AnalysisResult(
        List<CheckReport> checkReports,
        List<ConceptResult> conceptResults,
        List<Finding> findings,
        List<String> unparsableFiles) {

    public AnalysisResult {
        checkReports = List.copyOf(checkReports);
        conceptResults = List.copyOf(conceptResults);
        findings = List.copyOf(findings);
        unparsableFiles = List.copyOf(unparsableFiles);
    }

    /**
     * Whole-submission verdict for reporting and exit codes: did it meet every selected requirement?
     * {@code VIOLATION} if any check or concept is a proven violation (a demonstration concept that is
     * provably absent has no finding line, so we fold over check/concept states, not just findings);
     * else {@code UNDETERMINED} if anything is unknown or a file failed to parse; else {@code SATISFIED}.
     */
    public TriState overallState() {
        boolean anyViolation = checkReports.stream().anyMatch(r -> r.overall() == TriState.VIOLATION)
                || conceptResults.stream().anyMatch(c -> c.state() == TriState.VIOLATION);
        if (anyViolation) {
            return TriState.VIOLATION;
        }
        boolean anyUndetermined = checkReports.stream().anyMatch(r -> r.overall() == TriState.UNDETERMINED)
                || conceptResults.stream().anyMatch(c -> c.state() == TriState.UNDETERMINED)
                || !unparsableFiles.isEmpty();
        return anyUndetermined ? TriState.UNDETERMINED : TriState.SATISFIED;
    }

    public boolean hasViolation() {
        return overallState() == TriState.VIOLATION;
    }
}
