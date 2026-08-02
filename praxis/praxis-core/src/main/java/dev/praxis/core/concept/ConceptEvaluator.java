package dev.praxis.core.concept;

import dev.praxis.core.check.CheckReport;
import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a {@link Concept} against the atomic-check reports and gathers the explaining evidence.
 * This is where {@code UNDETERMINED}-propagation is realised (via {@link BoolExpr} / Kleene logic):
 * a concept is {@code SATISFIED} only when its expression proves true from proven inputs.
 */
public final class ConceptEvaluator {

    private ConceptEvaluator() {
    }

    /**
     * @param concept the definition to evaluate
     * @param reports the atomic-check reports available this run, keyed by check id
     */
    public static ConceptResult evaluate(Concept concept, Map<String, CheckReport> reports) {
        Map<String, TriState> states = new java.util.HashMap<>();
        for (Map.Entry<String, CheckReport> e : reports.entrySet()) {
            states.put(e.getKey(), e.getValue().overall());
        }
        TriState verdict = concept.expression().evaluate(states);

        // Evidence: every finding from the checks this concept references (order-independent;
        // ConceptResult sorts them deterministically).
        List<Finding> evidence = new ArrayList<>();
        for (String checkId : concept.referencedCheckIds()) {
            CheckReport report = reports.get(checkId);
            if (report != null) {
                evidence.addAll(report.findings());
            }
        }
        return new ConceptResult(concept.id(), verdict, explain(verdict, evidence.size()), evidence);
    }

    /**
     * A one-line, professor-readable reason for the verdict — crucially explaining a VIOLATION or
     * UNDETERMINED that carries no finding line (a concept that is provably or possibly absent has
     * nothing to point at, which is otherwise confusing).
     */
    private static String explain(TriState verdict, int evidenceCount) {
        return switch (verdict) {
            case SATISFIED -> evidenceCount > 0
                    ? "demonstrated (" + evidenceCount + " located witness" + (evidenceCount == 1 ? "" : "es") + ")"
                    : "satisfied — no issues found";
            case VIOLATION -> evidenceCount > 0
                    ? "see the " + evidenceCount + " finding" + (evidenceCount == 1 ? "" : "s") + " below"
                    : "not demonstrated — no evidence of this concept anywhere in the parsed code";
            case UNDETERMINED -> evidenceCount > 0
                    ? "unresolved — see the finding(s) below"
                    : "could not be confirmed — no evidence found, and its absence is not provable";
        };
    }
}
