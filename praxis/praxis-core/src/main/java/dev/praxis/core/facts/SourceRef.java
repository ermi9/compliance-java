package dev.praxis.core.facts;

import com.github.javaparser.ast.Node;
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
     * Builds a {@code SourceRef} from a parsed node. Falls back to line/column {@code 1} only
     * when position info is entirely absent (should not happen for parsed nodes); callers that
     * require positional evidence should treat a missing range conservatively.
     */
    public static SourceRef of(String file, Node node) {
        int line = node.getBegin().map(p -> p.line).orElse(1);
        int column = node.getBegin().map(p -> p.column).orElse(1);
        String snippet = firstLine(node);
        return new SourceRef(file, Math.max(line, 1), Math.max(column, 1), snippet);
    }

    private static String firstLine(Node node) {
        String text = node.toString();
        int nl = text.indexOf('\n');
        String line = (nl >= 0 ? text.substring(0, nl) : text).strip();
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }
}
