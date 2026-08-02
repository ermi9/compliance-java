package dev.praxis.core.model;

/**
 * Three-valued verdict used everywhere a check or concept produces a result.
 *
 * <p>Invariant 1 (zero false positives) is encoded here: whenever preconditions are not met
 * — parse failure, symbol resolution failure, ambiguous evidence — the correct answer is
 * {@link #UNDETERMINED}, never {@link #VIOLATION} and never {@link #SATISFIED}.
 *
 * <p>For boolean composition the mapping is: {@code SATISFIED == true}, {@code VIOLATION ==
 * false}, {@code UNDETERMINED == unknown}. Composition uses Kleene (three-valued) logic; see
 * {@link #and(TriState)}, {@link #or(TriState)}, {@link #not()}.
 */
public enum TriState {
    /** The property was proven to hold. */
    SATISFIED,
    /** The property was proven to be broken, with defensible evidence. */
    VIOLATION,
    /** Preconditions not met; the tool declines to guess. */
    UNDETERMINED;

    /**
     * Kleene AND. A proven {@link #VIOLATION} (false) dominates so real, defensible evidence
     * is never masked; otherwise any {@link #UNDETERMINED} yields {@link #UNDETERMINED}; only
     * all-{@link #SATISFIED} yields {@link #SATISFIED}.
     */
    public TriState and(TriState other) {
        if (this == VIOLATION || other == VIOLATION) {
            return VIOLATION;
        }
        if (this == UNDETERMINED || other == UNDETERMINED) {
            return UNDETERMINED;
        }
        return SATISFIED;
    }

    /**
     * Kleene OR. A proven {@link #SATISFIED} (true) dominates; otherwise any
     * {@link #UNDETERMINED} yields {@link #UNDETERMINED}; only all-{@link #VIOLATION} yields
     * {@link #VIOLATION}.
     */
    public TriState or(TriState other) {
        if (this == SATISFIED || other == SATISFIED) {
            return SATISFIED;
        }
        if (this == UNDETERMINED || other == UNDETERMINED) {
            return UNDETERMINED;
        }
        return VIOLATION;
    }

    /** Kleene NOT: swaps {@link #SATISFIED}/{@link #VIOLATION}, leaves {@link #UNDETERMINED}. */
    public TriState not() {
        return switch (this) {
            case SATISFIED -> VIOLATION;
            case VIOLATION -> SATISFIED;
            case UNDETERMINED -> UNDETERMINED;
        };
    }

    /**
     * AND-fold over many states (empty ⇒ {@link #SATISFIED}). This is exactly the aggregation an
     * atomic check uses across its subjects and a concept uses for AND: a proven {@link #VIOLATION}
     * dominates, else any {@link #UNDETERMINED} wins, else {@link #SATISFIED}.
     */
    public static TriState allOf(Iterable<TriState> states) {
        TriState acc = SATISFIED;
        for (TriState s : states) {
            acc = acc.and(s);
        }
        return acc;
    }

    /** OR-fold over many states (empty ⇒ {@link #VIOLATION}). */
    public static TriState anyOf(Iterable<TriState> states) {
        TriState acc = VIOLATION;
        for (TriState s : states) {
            acc = acc.or(s);
        }
        return acc;
    }
}
