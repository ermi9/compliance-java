package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.facts.TypedNewFact;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * Coercion polymorphism: a subtype instance is bound to a supertype-typed target
 * ({@code User u = new AdminUser();}, a field, or a {@code return}). Proven soundly by pairing an
 * AST-decidable {@link TypedNewFact} with the project {@link TypeHierarchy}. Absence is not decidable
 * (upcasts also occur via casts and call arguments we don't model), so no-evidence ⇒ UNDETERMINED.
 */
public final class CoercionCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.COERCION_UPCAST;
    }

    @Override
    public String description() {
        return "A subtype instance is used as a supertype (coercion polymorphism).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypedNewFact tn : facts.typedNews()) {
            if (hierarchy.isProperSubtype(tn.createdTypeName(), tn.targetTypeName())) {
                evidence.add(evidence(tn.source(),
                        "Coercion: '" + tn.createdTypeName() + "' instance used as '" + tn.targetTypeName()
                                + "' (" + tn.site().name().toLowerCase() + ")."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return false;
    }
}
