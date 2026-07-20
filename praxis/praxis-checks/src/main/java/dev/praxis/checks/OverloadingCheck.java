package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.ConstructorFact;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.MethodFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ad-hoc polymorphism: within one type, a method name (or the constructor) has two or more overloads
 * with distinct erased parameter signatures. Absence is decidable.
 */
public final class OverloadingCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.OVERLOADS;
    }

    @Override
    public String description() {
        return "A method or constructor is overloaded (ad-hoc polymorphism).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            // Methods grouped by name; distinct erased signatures ⇒ overload.
            Map<String, Set<String>> signaturesByName = new LinkedHashMap<>();
            Map<String, MethodFact> firstByName = new LinkedHashMap<>();
            for (MethodFact m : t.methods()) {
                signaturesByName.computeIfAbsent(m.name(), k -> new HashSet<>()).add(m.erasedSignature());
                firstByName.putIfAbsent(m.name(), m);
            }
            for (var e : signaturesByName.entrySet()) {
                if (e.getValue().size() >= 2) {
                    MethodFact m = firstByName.get(e.getKey());
                    evidence.add(evidence(m.source(),
                            "Overloading: method '" + e.getKey() + "' has " + e.getValue().size()
                                    + " overloads in '" + t.simpleName() + "'."));
                }
            }
            // Overloaded constructors.
            Set<String> ctorSignatures = new HashSet<>();
            for (ConstructorFact c : t.constructors()) {
                ctorSignatures.add(String.join(",", c.erasedParamTypeNames()));
            }
            if (ctorSignatures.size() >= 2 && !t.constructors().isEmpty()) {
                evidence.add(evidence(t.constructors().get(0).source(),
                        "Overloading: '" + t.simpleName() + "' has " + ctorSignatures.size() + " constructors."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
