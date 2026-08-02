package dev.praxis.checks;

import dev.praxis.core.check.Check;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.List;

/**
 * Base for "did the student demonstrate concept X?" checks. These differ from representation-leak
 * checks: there is no per-subject verdict, only project-wide evidence.
 *
 * <p>Honest tri-state semantics (invariant 1):
 * <ul>
 *   <li><b>SATISFIED</b> — at least one located piece of evidence was found (each evidence is a
 *       {@link Finding} carrying a real line, so a green is always defensible).</li>
 *   <li><b>VIOLATION</b> — no evidence AND absence is <em>decidable</em> from complete declarations
 *       AND the whole corpus parsed. Only then can we prove the concept is absent.</li>
 *   <li><b>UNDETERMINED</b> — no evidence but absence is not provable (parse gaps, or a concept whose
 *       absence cannot be soundly concluded, e.g. dynamic dispatch). Never a false verdict.</li>
 * </ul>
 */
abstract class DemonstrationCheck implements Check {

    /** Cap on emitted evidence findings so output stays readable and deterministic. */
    private static final int MAX_EVIDENCE = 5;

    /** Located, SATISFIED-state evidence of the concept. Empty ⇒ not demonstrated (as far as we can tell). */
    protected abstract List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options);

    /**
     * Whether "no evidence" can be soundly read as "concept absent" (given a fully-parsed corpus).
     * True for declaration-level concepts (we see every declaration); false for concepts whose use we
     * can only positively confirm (coercion, dynamic dispatch, composition via library types).
     */
    protected abstract boolean absenceIsDecidable();

    @Override
    public final CheckReport evaluate(FactModel facts, CheckOptions options) {
        TypeHierarchy hierarchy = new TypeHierarchy(facts.types());
        List<Finding> evidence = findEvidence(facts, hierarchy, options);
        if (!evidence.isEmpty()) {
            List<Finding> capped = evidence.stream().sorted().limit(MAX_EVIDENCE).toList();
            return new CheckReport(id(), TriState.SATISFIED, capped);
        }
        if (absenceIsDecidable() && facts.fullyParsed()) {
            return new CheckReport(id(), TriState.VIOLATION, List.of());
        }
        return new CheckReport(id(), TriState.UNDETERMINED, List.of());
    }

    /** Helper for subclasses to build a SATISFIED evidence finding. */
    protected Finding evidence(dev.praxis.core.facts.SourceRef at, String explanation) {
        return new Finding(id(), TriState.SATISFIED, at.file(), at.line(), at.column(), at.snippet(), explanation);
    }
}
