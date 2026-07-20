package dev.praxis.core.facts;

import com.github.javaparser.ast.Node;
import java.util.List;
import java.util.Objects;

/**
 * A neutral pointer into submission source: file path plus a 1-based line/column and the
 * offending line's text. Every fact that a check can flag exposes one so findings can satisfy
 * invariant 5 (every finding carries evidence with a line number).
 */
public record SourceRef(String file, int line, int column, String snippet) {

    public SourceRef {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(snippet, "snippet");
    }

    /**
     * Builds a {@code SourceRef} whose snippet is the true physical source line the node begins on,
     * read from {@code sourceLines} (0-indexed). Using the real line — not a reconstruction of the
     * AST — keeps evidence faithful (invariant 5) even when comments are attached to the node.
     */
    public static SourceRef of(String file, Node node, List<String> sourceLines) {
        int line = Math.max(node.getBegin().map(p -> p.line).orElse(1), 1);
        int column = Math.max(node.getBegin().map(p -> p.column).orElse(1), 1);
        String snippet = physicalLine(sourceLines, line);
        return new SourceRef(file, line, column, snippet);
    }

    private static String physicalLine(List<String> sourceLines, int line) {
        if (line >= 1 && line <= sourceLines.size()) {
            String text = sourceLines.get(line - 1).strip();
            return text.length() > 200 ? text.substring(0, 200) + "…" : text;
        }
        return "";
    }
}
