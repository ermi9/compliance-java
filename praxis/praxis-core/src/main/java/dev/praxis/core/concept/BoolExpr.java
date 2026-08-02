package dev.praxis.core.concept;

import dev.praxis.core.model.TriState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A boolean expression over atomic-check ids, evaluated with Kleene three-valued logic. This is the
 * <em>only</em> place Layer-3 composition lives; a concept holds one of these. Expressions carry no
 * analysis logic themselves — they reference checks by id and combine their verdicts.
 */
public sealed interface BoolExpr permits BoolExpr.CheckRef, BoolExpr.And, BoolExpr.Or, BoolExpr.Not {

    /**
     * Evaluates against the checks' aggregate verdicts. A referenced check that is absent from
     * {@code checkStates} (not run, or unavailable) evaluates to {@code UNDETERMINED} — never a
     * verdict — preserving invariant 1.
     */
    TriState evaluate(Map<String, TriState> checkStates);

    /** Collects every check id this expression references. */
    void collectRefs(Set<String> into);

    default Set<String> referencedCheckIds() {
        Set<String> refs = new TreeSet<>();
        collectRefs(refs);
        return refs;
    }

    static BoolExpr check(String checkId) {
        return new CheckRef(checkId);
    }

    static BoolExpr and(BoolExpr... operands) {
        return new And(List.of(operands));
    }

    static BoolExpr or(BoolExpr... operands) {
        return new Or(List.of(operands));
    }

    static BoolExpr not(BoolExpr operand) {
        return new Not(operand);
    }

    /** Leaf: the aggregate verdict of a single atomic check. */
    record CheckRef(String checkId) implements BoolExpr {
        @Override
        public TriState evaluate(Map<String, TriState> checkStates) {
            return checkStates.getOrDefault(checkId, TriState.UNDETERMINED);
        }

        @Override
        public void collectRefs(Set<String> into) {
            into.add(checkId);
        }
    }

    /** Kleene AND over operands (empty ⇒ {@code SATISFIED}). */
    record And(List<BoolExpr> operands) implements BoolExpr {
        @Override
        public TriState evaluate(Map<String, TriState> checkStates) {
            return TriState.allOf(operands.stream().map(o -> o.evaluate(checkStates)).toList());
        }

        @Override
        public void collectRefs(Set<String> into) {
            operands.forEach(o -> o.collectRefs(into));
        }
    }

    /** Kleene OR over operands (empty ⇒ {@code VIOLATION}). */
    record Or(List<BoolExpr> operands) implements BoolExpr {
        @Override
        public TriState evaluate(Map<String, TriState> checkStates) {
            return TriState.anyOf(operands.stream().map(o -> o.evaluate(checkStates)).toList());
        }

        @Override
        public void collectRefs(Set<String> into) {
            operands.forEach(o -> o.collectRefs(into));
        }
    }

    /** Kleene NOT. */
    record Not(BoolExpr operand) implements BoolExpr {
        @Override
        public TriState evaluate(Map<String, TriState> checkStates) {
            return operand.evaluate(checkStates).not();
        }

        @Override
        public void collectRefs(Set<String> into) {
            operand.collectRefs(into);
        }
    }
}
