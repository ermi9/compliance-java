package dev.praxis.core.concept;

import java.util.Set;

/**
 * Layer 3 — a named composition of atomic checks that encodes one of the course's <em>definitions</em>
 * of an OOP concept. The definition is the {@link BoolExpr}; it references atomic checks by id and
 * combines their verdicts with Kleene logic. A concept holds no analysis logic of its own.
 *
 * @param id          stable identifier used by rulesets (e.g. {@code encapsulation})
 * @param description one-line human description of the definition
 * @param expression  the boolean composition over atomic-check ids
 */
public record Concept(String id, String description, BoolExpr expression) {

    /** The atomic-check ids this concept depends on. */
    public Set<String> referencedCheckIds() {
        return expression.referencedCheckIds();
    }
}
