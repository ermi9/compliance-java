package dev.praxis.cli;

import dev.praxis.core.concept.ConceptResult;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.model.Finding;

/**
 * Renders an {@link AnalysisResult} as deterministic, human-readable text. Every line derives from
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

        sb.append("\nConcepts:\n");
        if (result.conceptResults().isEmpty()) {
            sb.append("  (none evaluated)\n");
        } else {
            for (ConceptResult c : result.conceptResults()) {
                sb.append("  ").append(c.conceptId()).append(": ").append(c.state()).append('\n');
            }
        }

        sb.append("\nFindings (").append(result.findings().size()).append("):\n");
        if (result.findings().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Finding f : result.findings()) {
                sb.append("  [").append(f.state()).append("] ")
                        .append(f.checkId()).append("  ")
                        .append(f.file()).append(':').append(f.line()).append(':').append(f.column()).append('\n');
                sb.append("      ").append(f.snippet()).append('\n');
                sb.append("      ").append(f.explanation()).append('\n');
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
}
