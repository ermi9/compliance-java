package dev.praxis.cli;

import dev.praxis.core.concept.ConceptResult;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.model.Finding;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders an {@link AnalysisResult} as deterministic, human-readable text, grouped by OOP principle
 * (concept) so each verdict sits next to the exact evidence that produced it. Every line derives from
 * already-sorted data, so identical input yields byte-identical output (invariant 4).
 */
final class TextReportWriter {

    private TextReportWriter() {
    }

    static String render(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Praxis report\n");
        sb.append("=============\n");
        sb.append("Verdict: ").append(result.overallState()).append('\n');

        // At-a-glance scorecard.
        sb.append("\nSummary:\n");
        if (result.conceptResults().isEmpty()) {
            sb.append("  (no concepts evaluated)\n");
        } else {
            int width = result.conceptResults().stream().mapToInt(c -> c.conceptId().length()).max().orElse(0);
            for (ConceptResult c : result.conceptResults()) {
                sb.append("  ").append(pad(c.conceptId(), width)).append("  ").append(c.state()).append('\n');
            }
        }

        // Details grouped under each principle, with that principle's evidence findings.
        sb.append("\nDetails by principle:\n");
        Set<Finding> covered = new LinkedHashSet<>();
        for (ConceptResult c : result.conceptResults()) {
            sb.append("\n  ── ").append(c.conceptId()).append(" ──  ")
                    .append(c.state()).append(" — ").append(c.explanation()).append('\n');
            if (c.evidence().isEmpty()) {
                sb.append("      (no located evidence)\n");
            } else {
                for (Finding f : c.evidence()) {
                    appendFinding(sb, f);
                    covered.add(f);
                }
            }
        }

        // Any findings from checks that aren't part of a listed concept (so nothing is hidden).
        List<Finding> other = result.findings().stream().filter(f -> !covered.contains(f)).toList();
        if (!other.isEmpty()) {
            sb.append("\n  ── other checks ──\n");
            for (Finding f : other) {
                appendFinding(sb, f);
            }
        }

        sb.append("\nUnparsable files: ");
        if (result.unparsableFiles().isEmpty()) {
            sb.append("none\n");
        } else {
            sb.append('\n');
            for (String file : result.unparsableFiles()) {
                sb.append("  ").append(file).append('\n');
            }
        }
        return sb.toString();
    }

    private static void appendFinding(StringBuilder sb, Finding f) {
        sb.append("      [").append(f.state()).append("] ")
                .append(f.checkId()).append("  ")
                .append(f.file()).append(':').append(f.line()).append(':').append(f.column()).append('\n');
        sb.append("          ").append(f.snippet()).append('\n');
        sb.append("          ").append(f.explanation()).append('\n');
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}
