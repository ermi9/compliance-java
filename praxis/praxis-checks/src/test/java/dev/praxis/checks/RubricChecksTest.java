package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.concept.Concept;
import dev.praxis.core.concept.Concepts;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.engine.Engine;
import dev.praxis.core.model.TriState;
import dev.praxis.core.ruleset.Ruleset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exercises the full rubric (all demonstration concepts) against a rich and a barren fixture. */
class RubricChecksTest {

    private final Engine engine = new Engine(BuiltInChecks.all(), Concepts.builtIn());
    private final Ruleset allConcepts = new Ruleset(
            1,
            Concepts.builtIn().stream().map(Concept::id).collect(Collectors.toSet()),
            Set.of(),
            Map.of());

    private Map<String, TriState> states(String fixture) {
        AnalysisResult result = engine.run(TestFixtures.facts(fixture), allConcepts);
        return result.conceptResults().stream()
                .collect(Collectors.toMap(c -> c.conceptId(), c -> c.state()));
    }

    @Test
    void richProjectDemonstratesTheConcepts() {
        Map<String, TriState> s = states("rubric/rich");

        // Concepts with a located witness must be SATISFIED.
        assertThat(s).containsEntry("inheritance", TriState.SATISFIED)
                .containsEntry("composition", TriState.SATISFIED)
                .containsEntry("abstraction", TriState.SATISFIED)
                .containsEntry("subtyping", TriState.SATISFIED)
                .containsEntry("polymorphism_overloading", TriState.SATISFIED)
                .containsEntry("polymorphism_parametric", TriState.SATISFIED)
                .containsEntry("polymorphism_coercion", TriState.SATISFIED)
                .containsEntry("exception_handling", TriState.SATISFIED)
                .containsEntry("extensibility", TriState.SATISFIED);

        // Inclusion dispatch depends on receiver resolution; it must at least never be a false VIOLATION.
        assertThat(s.get("polymorphism_inclusion")).isNotEqualTo(TriState.VIOLATION);
    }

    @Test
    void barrenProjectFailsDecidableConceptsAndNeverFalselyViolatesTheRest() {
        Map<String, TriState> s = states("rubric/barren");

        // Decidable concepts are provably absent -> VIOLATION.
        assertThat(s).containsEntry("abstraction", TriState.VIOLATION)
                .containsEntry("inheritance", TriState.VIOLATION)
                .containsEntry("subtyping", TriState.VIOLATION)
                .containsEntry("polymorphism_overloading", TriState.VIOLATION)
                .containsEntry("polymorphism_parametric", TriState.VIOLATION)
                .containsEntry("exception_handling", TriState.VIOLATION);

        // Non-decidable concepts must degrade to UNDETERMINED, never a false VIOLATION (invariant 1).
        assertThat(s).containsEntry("composition", TriState.UNDETERMINED)
                .containsEntry("polymorphism_coercion", TriState.UNDETERMINED)
                .containsEntry("polymorphism_inclusion", TriState.UNDETERMINED)
                .containsEntry("extensibility", TriState.UNDETERMINED);
    }
}
