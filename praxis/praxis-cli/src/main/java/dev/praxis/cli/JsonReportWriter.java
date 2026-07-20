package dev.praxis.cli;

import dev.praxis.core.check.CheckReport;
import dev.praxis.core.concept.ConceptResult;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.model.Finding;
import java.util.List;

/**
 * Renders an {@link AnalysisResult} as deterministic JSON. Hand-written (no JSON dependency) with a
 * fixed key order and pre-sorted collections, so identical input yields byte-identical output.
 */
final class JsonReportWriter {

    private JsonReportWriter() {
    }

    static String render(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"verdict\": ").append(quote(result.overallState().name())).append(",\n");

        sb.append("  \"concepts\": ");
        appendConcepts(sb, result.conceptResults());
        sb.append(",\n");

        sb.append("  \"checks\": ");
        appendChecks(sb, result.checkReports());
        sb.append(",\n");

        sb.append("  \"findings\": ");
        appendFindings(sb, result.findings());
        sb.append(",\n");

        sb.append("  \"unparsableFiles\": ");
        appendStringArray(sb, result.unparsableFiles());
        sb.append('\n');

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendConcepts(StringBuilder sb, List<ConceptResult> concepts) {
        if (concepts.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < concepts.size(); i++) {
            ConceptResult c = concepts.get(i);
            sb.append("    { \"id\": ").append(quote(c.conceptId()))
                    .append(", \"state\": ").append(quote(c.state().name()))
                    .append(", \"explanation\": ").append(quote(c.explanation())).append(" }");
            sb.append(i < concepts.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]");
    }

    private static void appendChecks(StringBuilder sb, List<CheckReport> checks) {
        if (checks.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < checks.size(); i++) {
            CheckReport c = checks.get(i);
            sb.append("    { \"id\": ").append(quote(c.checkId()))
                    .append(", \"state\": ").append(quote(c.overall().name())).append(" }");
            sb.append(i < checks.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]");
    }

    private static void appendFindings(StringBuilder sb, List<Finding> findings) {
        if (findings.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("    {\n");
            sb.append("      \"checkId\": ").append(quote(f.checkId())).append(",\n");
            sb.append("      \"state\": ").append(quote(f.state().name())).append(",\n");
            sb.append("      \"file\": ").append(quote(f.file())).append(",\n");
            sb.append("      \"line\": ").append(f.line()).append(",\n");
            sb.append("      \"column\": ").append(f.column()).append(",\n");
            sb.append("      \"snippet\": ").append(quote(f.snippet())).append(",\n");
            sb.append("      \"explanation\": ").append(quote(f.explanation())).append('\n');
            sb.append("    }");
            sb.append(i < findings.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]");
    }

    private static void appendStringArray(StringBuilder sb, List<String> values) {
        if (values.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < values.size(); i++) {
            sb.append("    ").append(quote(values.get(i)));
            sb.append(i < values.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]");
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
