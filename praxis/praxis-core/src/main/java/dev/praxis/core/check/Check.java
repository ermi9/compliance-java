package dev.praxis.core.check;

import dev.praxis.core.facts.FactModel;

/**
 * Layer 2 — the atomic-check SPI. An implementation is a small, independently testable predicate
 * over the {@link FactModel} that is provable-or-{@code UNDETERMINED}.
 *
 * <p>Implementations MUST honour invariant 1: when preconditions are not met (symbol resolution
 * failed, parse error, ambiguous evidence), the subject's verdict is {@code UNDETERMINED} — never
 * {@code VIOLATION} and never {@code SATISFIED}. They MUST NOT execute or compile submission code
 * (invariant 2) and MUST NOT let submission names/annotations/paths steer behaviour (invariant 6).
 */
public interface Check {

    /** Stable identifier, referenced by rulesets and concepts (e.g. {@code field.no-public-mutable}). */
    String id();

    /** One-line human description of what the check proves. */
    String description();

    /** Runs the check over all facts and returns its aggregate verdict plus evidence. */
    CheckReport evaluate(FactModel facts, CheckOptions options);
}
