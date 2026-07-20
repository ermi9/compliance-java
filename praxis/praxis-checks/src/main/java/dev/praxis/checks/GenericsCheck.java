package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.MethodFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * Parametric polymorphism: the project declares a generic type ({@code class Box<T>}) or a generic
 * method ({@code <T> T first(List<T>)}). Merely <em>using</em> JDK generics (e.g. {@code List<String>})
 * does not count — the demonstration is declaring one's own type parameter. Absence is decidable.
 */
public final class GenericsCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.DECLARES_GENERIC;
    }

    @Override
    public String description() {
        return "A generic type or generic method is declared (parametric polymorphism).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (t.isGeneric()) {
                evidence.add(evidence(t.source(),
                        "Parametric: generic type '" + t.simpleName() + "<" + String.join(",", t.typeParameters()) + ">'."));
            }
            for (MethodFact m : t.methods()) {
                if (m.isGeneric()) {
                    evidence.add(evidence(m.source(),
                            "Parametric: generic method '" + m.name() + "' declares <" + String.join(",", m.typeParameters()) + ">."));
                }
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
