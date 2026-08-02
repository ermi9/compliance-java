package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * Subtyping / multityping: a type implements one or more interfaces. A type implementing two or more
 * interfaces is called out as multityping in the evidence. Absence is decidable.
 */
public final class SubtypingCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.IMPLEMENTS_INTERFACE;
    }

    @Override
    public String description() {
        return "A type implements one or more interfaces (subtyping / multityping).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (!t.implementedTypeNames().isEmpty()) {
                boolean multi = t.implementedInterfaceCount() >= 2;
                evidence.add(evidence(t.source(),
                        "Subtyping: '" + t.simpleName() + "' implements " + String.join(", ", t.implementedTypeNames())
                                + (multi ? " (multityping)" : "") + "."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
