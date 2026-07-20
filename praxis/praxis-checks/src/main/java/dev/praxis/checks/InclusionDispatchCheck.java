package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.CallSiteFact;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.MethodFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inclusion polymorphism (provisional; the full flagship type-flow analysis is a later session).
 *
 * <p>Positive-evidence only: it pairs a call site whose receiver's declared static type is a project
 * supertype {@code P} with the fact that some subtype of {@code P} overrides the called method. That
 * combination is a sound witness that a virtual call is dispatched dynamically. Because receiver-type
 * resolution is best-effort and this is not exhaustive, absence is NOT decidable ⇒ UNDETERMINED, never
 * a claim that the student failed to use dynamic dispatch.
 */
public final class InclusionDispatchCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.INCLUSION_DISPATCH;
    }

    @Override
    public String description() {
        return "An overridden method is invoked through a supertype reference (dynamic dispatch).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        // Pairs "SupertypeSimpleName#methodName" for methods that a subtype overrides from a project supertype.
        Set<String> overriddenViaSupertype = new HashSet<>();
        for (TypeFact t : facts.types()) {
            for (MethodFact m : t.methods()) {
                hierarchy.overriddenSupertype(t, m)
                        .ifPresent(sup -> overriddenViaSupertype.add(sup.simpleName() + "#" + m.name()));
            }
        }

        List<Finding> evidence = new ArrayList<>();
        for (CallSiteFact call : facts.callSites()) {
            if (call.receiverDeclaredType() == null) {
                continue;
            }
            String recv = simpleName(call.receiverDeclaredType());
            if (overriddenViaSupertype.contains(recv + "#" + call.methodName())) {
                evidence.add(evidence(call.source(),
                        "Inclusion: '" + call.methodName() + "()' dispatched via supertype reference '"
                                + recv + "' (overridden in a subtype)."));
            }
        }
        return evidence;
    }

    /** Reduces a resolved type description like {@code a.b.User<X>} to its simple name {@code User}. */
    private static String simpleName(String describe) {
        String noGenerics = describe.contains("<") ? describe.substring(0, describe.indexOf('<')) : describe;
        int dot = noGenerics.lastIndexOf('.');
        return dot >= 0 ? noGenerics.substring(dot + 1) : noGenerics;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return false;
    }
}
