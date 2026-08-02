package dev.praxis.checks;

import dev.praxis.core.check.Check;
import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.FieldFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.ArrayList;
import java.util.List;

/**
 * Information hiding — the <b>constancy</b> step of the three-step per-field discipline (constancy,
 * mutability, visibility): a field that is never reassigned after construction must be declared
 * {@code final}.
 *
 * <p>Sound and conservative (invariant 1). A verdict is only emitted for {@code private} fields, whose
 * every assignment is contained in the declaring type — and only when that type is top-level, so the
 * assignment analysis is complete:
 * <ul>
 *   <li>{@code final} field ⇒ SATISFIED (constancy already correct);</li>
 *   <li>non-final, provably never reassigned ⇒ VIOLATION ("should be final");</li>
 *   <li>non-final and reassigned somewhere ⇒ SATISFIED (correctly mutable);</li>
 *   <li>non-private, or assignments not fully visible ⇒ not judged here (UNDETERMINED / left to the
 *       visibility step) — never guessed.</li>
 * </ul>
 */
public final class FieldConstancyCheck implements Check {

    @Override
    public String id() {
        return CheckIds.FIELD_CONSTANCY;
    }

    @Override
    public String description() {
        return "A field never reassigned after construction is declared final (constancy).";
    }

    @Override
    public CheckReport evaluate(FactModel facts, CheckOptions options) {
        List<TriState> subjects = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        for (TypeFact type : facts.types()) {
            for (FieldFact field : type.fields()) {
                if (!field.isInstanceField()) {
                    continue;
                }
                if (field.isFinal()) {
                    subjects.add(TriState.SATISFIED); // constancy already expressed
                    continue;
                }
                if (!field.isPrivate()) {
                    // Visibility step owns non-private fields; external code could reassign, so we
                    // cannot prove constancy here — stay silent rather than risk a false positive.
                    continue;
                }
                if (!field.assignmentsFullyVisible()) {
                    subjects.add(TriState.UNDETERMINED);
                    continue;
                }
                if (field.isReassignable()) {
                    subjects.add(TriState.SATISFIED); // genuinely mutable ⇒ correctly non-final
                } else {
                    subjects.add(TriState.VIOLATION);
                    findings.add(new Finding(
                            id(),
                            TriState.VIOLATION,
                            field.source().file(),
                            field.source().line(),
                            field.source().column(),
                            field.source().snippet(),
                            "Field '" + field.name() + "' is never reassigned after construction, so the"
                                    + " constancy step requires it to be declared 'final'."));
                }
            }
        }
        return CheckReport.of(id(), subjects, findings);
    }
}
