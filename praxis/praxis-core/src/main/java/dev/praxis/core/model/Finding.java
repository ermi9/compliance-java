package dev.praxis.core.model;

import java.util.Objects;

/**
 * A single piece of evidence produced by an atomic check.
 *
 * <p>Invariant 5: every finding carries evidence — check id, file, line, column, the
 * offending source snippet, and a human explanation. A finding without a line number is a
 * bug, so {@code line} and {@code column} are validated as positive at construction.
 *
 * @param checkId     id of the atomic check that produced this finding
 * @param state       the verdict this finding records
 * @param file        submission-relative source path
 * @param line        1-based line of the offending element
 * @param column      1-based column of the offending element
 * @param snippet     the offending source text (single line, trimmed)
 * @param explanation human-readable, professor-defensible reason
 */
public record Finding(
        String checkId,
        TriState state,
        String file,
        int line,
        int column,
        String snippet,
        String explanation) implements Comparable<Finding> {

    public Finding {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(snippet, "snippet");
        Objects.requireNonNull(explanation, "explanation");
        if (line < 1) {
            throw new IllegalArgumentException("finding must have a 1-based line, got " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("finding must have a 1-based column, got " + column);
        }
    }

    /**
     * Deterministic ordering (invariant 4): by file, then line, then column, then check id,
     * then state, then snippet. Total and stable so output is byte-identical across runs.
     */
    @Override
    public int compareTo(Finding o) {
        int c = file.compareTo(o.file);
        if (c != 0) return c;
        c = Integer.compare(line, o.line);
        if (c != 0) return c;
        c = Integer.compare(column, o.column);
        if (c != 0) return c;
        c = checkId.compareTo(o.checkId);
        if (c != 0) return c;
        c = state.compareTo(o.state);
        if (c != 0) return c;
        return snippet.compareTo(o.snippet);
    }
}
