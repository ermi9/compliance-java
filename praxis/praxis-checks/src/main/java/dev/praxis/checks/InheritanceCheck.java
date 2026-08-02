package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/** Inheritance: a type extends a supertype (class or interface). Absence is decidable. */
public final class InheritanceCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.USES_INHERITANCE;
    }

    @Override
    public String description() {
        return "A type extends a supertype (inheritance).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (!t.extendedTypeNames().isEmpty()) {
                evidence.add(evidence(t.source(),
                        "Inheritance: '" + t.simpleName() + "' extends " + String.join(", ", t.extendedTypeNames()) + "."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
