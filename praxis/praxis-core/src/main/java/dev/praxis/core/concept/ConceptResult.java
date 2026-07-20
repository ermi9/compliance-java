package dev.praxis.core.concept;

import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.ArrayList;
import java.util.List;

/**
 * The evaluated verdict of one concept plus the findings (drawn from its referenced checks) that
 * explain it. Evidence is stored sorted for deterministic output.
 */
public record ConceptResult(String conceptId, TriState state, List<Finding> evidence) {

    public ConceptResult {
        List<Finding> sorted = new ArrayList<>(evidence);
        sorted.sort(null);
        evidence = List.copyOf(sorted);
    }
}
