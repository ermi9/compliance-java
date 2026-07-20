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
     * Whole-submission verdict for reporting and exit codes: {@code VIOLATION} if any finding is a
     * proven violation, else {@code UNDETERMINED} if anything is unknown, else {@code SATISFIED}.
     */
    public TriState overallState() {
        boolean anyViolation = findings.stream().anyMatch(f -> f.state() == TriState.VIOLATION);
        if (anyViolation) {
            return TriState.VIOLATION;
        }
        boolean anyUndetermined = findings.stream().anyMatch(f -> f.state() == TriState.UNDETERMINED)
                || conceptResults.stream().anyMatch(c -> c.state() == TriState.UNDETERMINED)
                || !unparsableFiles.isEmpty();
        return anyUndetermined ? TriState.UNDETERMINED : TriState.SATISFIED;
    }

    public boolean hasViolation() {
        return overallState() == TriState.VIOLATION;
    }
}
