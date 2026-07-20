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
 * Extensibility (provisional): an abstraction (interface / abstract class) that is actually extended
 * or implemented by a project subtype — an exercised open-for-extension seam. Absence is not
 * decidable (an unused seam is still extensible), so no-evidence ⇒ UNDETERMINED.
 */
public final class ExtensibilityCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.EXTENSIBILITY;
    }

    @Override
    public String description() {
        return "An abstraction is extended/implemented by a subtype (open-for-extension seam).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (!t.isAbstraction()) {
                continue;
            }
            int subs = hierarchy.subtypesOf(t).size();
            if (subs > 0) {
                evidence.add(evidence(t.source(),
                        "Extensibility: abstraction '" + t.simpleName() + "' is extended/implemented by "
                                + subs + " project type(s)."));
            }
        }
        return evidence;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return false;
    }
}
