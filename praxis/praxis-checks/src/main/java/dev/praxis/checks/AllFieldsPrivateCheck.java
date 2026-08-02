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
 * Information hiding (provisional definition — pending professor sign-off on the "three-step"
 * wording): every instance field is {@code private}, so representation is hidden behind the type's
 * API. This is a quality check (VIOLATION on a proven exposed field), not a demonstration check.
 * Modifiers are reliable from the AST, so it never needs UNDETERMINED for a parsed type.
 */
public final class AllFieldsPrivateCheck implements Check {

    @Override
    public String id() {
        return CheckIds.ALL_FIELDS_PRIVATE;
    }

    @Override
    public String description() {
        return "Every instance field is private (information hiding).";
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
                if (field.isPrivate()) {
                    subjects.add(TriState.SATISFIED);
                } else {
                    subjects.add(TriState.VIOLATION);
                    findings.add(new Finding(
                            id(),
                            TriState.VIOLATION,
                            field.source().file(),
                            field.source().line(),
                            field.source().column(),
                            field.source().snippet(),
                            "Instance field '" + field.name() + "' is not private, so internal representation is"
                                    + " not hidden; make it private and expose behaviour through methods."));
                }
            }
        }
        return CheckReport.of(id(), subjects, findings);
    }
}
