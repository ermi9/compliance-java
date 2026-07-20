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
 * Atomic check: no non-{@code private}, non-{@code final} instance field exposes internal state.
 *
 * <p>Such a field is a representation leak — any caller can read and reassign it. Modifiers come
 * straight from the parsed AST, so this predicate is provable without symbol resolution; it therefore
 * emits {@code VIOLATION} or {@code SATISFIED} per field and never needs {@code UNDETERMINED} for a
 * parsed type. Static (class) fields are out of scope for this instance-state check.
 */
public final class PublicMutableFieldCheck implements Check {

    @Override
    public String id() {
        return CheckIds.NO_PUBLIC_MUTABLE_FIELD;
    }

    @Override
    public String description() {
        return "No non-private, non-final instance field exposes internal state directly.";
    }

    @Override
    public CheckReport evaluate(FactModel facts, CheckOptions options) {
        List<TriState> subjects = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        for (TypeFact type : facts.types()) {
            for (FieldFact field : type.fields()) {
                if (!field.isInstanceField()) {
                    continue; // static/class fields are not instance state
                }
                boolean exposed = !field.isPrivate() && !field.isFinal();
                if (exposed) {
                    subjects.add(TriState.VIOLATION);
                    findings.add(new Finding(
                            id(),
                            TriState.VIOLATION,
                            field.source().file(),
                            field.source().line(),
                            field.source().column(),
                            field.source().snippet(),
                            "Instance field '" + field.name() + "' is " + visibility(field)
                                    + " and non-final, exposing internal state directly; make it private"
                                    + " (and prefer final) and expose access through methods."));
                } else {
                    subjects.add(TriState.SATISFIED);
                }
            }
        }
        return CheckReport.of(id(), subjects, findings);
    }

    private static String visibility(FieldFact field) {
        if (field.isPublic()) return "public";
        if (field.isProtected()) return "protected";
        if (field.isPackagePrivate()) return "package-private";
        return "private";
    }
}
