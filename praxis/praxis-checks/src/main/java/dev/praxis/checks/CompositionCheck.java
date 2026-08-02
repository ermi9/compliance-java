package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.FieldFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Composition (has-a): an instance field references another project type — directly
 * ({@code Offer offer}) or inside a container ({@code List<Event> events}). Absence is <em>not</em>
 * decidable (composition can also be with library types we don't model), so no-evidence ⇒
 * {@code UNDETERMINED}, never {@code VIOLATION}.
 */
public final class CompositionCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.USES_COMPOSITION;
    }

    @Override
    public String description() {
        return "A type has-a field of another project type (composition).";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        Set<String> projectTypeNames = new HashSet<>();
        for (TypeFact t : facts.types()) {
            projectTypeNames.add(t.simpleName());
        }
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            for (FieldFact f : t.fields()) {
                if (!f.isInstanceField()) {
                    continue;
                }
                String referenced = referencedProjectType(f.declaredTypeName(), projectTypeNames, t.simpleName());
                if (referenced != null) {
                    evidence.add(evidence(f.source(),
                            "Composition: '" + t.simpleName() + "' has-a '" + referenced + "' via field '" + f.name() + "'."));
                }
            }
        }
        return evidence;
    }

    /** First project type name appearing as an identifier token in {@code declaredTypeName}, excluding self. */
    private static String referencedProjectType(String declaredTypeName, Set<String> projectTypeNames, String self) {
        for (String token : Pattern.compile("[^A-Za-z0-9_]+").split(declaredTypeName)) {
            if (!token.isBlank() && !token.equals(self) && projectTypeNames.contains(token)) {
                return token;
            }
        }
        return null;
    }

    @Override
    protected boolean absenceIsDecidable() {
        return false; // composition via library types is real but not modelled
    }
}
