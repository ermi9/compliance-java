package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/** Abstraction: the project declares an interface or an abstract class. Absence is decidable. */
public final class AbstractionCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.DECLARES_ABSTRACTION;
    }

    @Override
    public String description() {
        return "The project declares an abstraction (interface or abstract class).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (t.isAbstraction()) {
                String kind = t.kind() == TypeFact.Kind.INTERFACE ? "interface" : "abstract class";
                evidence.add(evidence(t.source(), "Abstraction: " + kind + " '" + t.simpleName() + "'."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
