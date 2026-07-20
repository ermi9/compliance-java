package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.concept.Concepts;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.engine.Engine;
import dev.praxis.core.model.TriState;
import dev.praxis.core.ruleset.Ruleset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves Layer-3 AND-composition and UNDETERMINED-propagation of the Encapsulation concept. */
class EncapsulationConceptTest {

    private final Engine engine = new Engine(BuiltInChecks.all(), Concepts.builtIn());
    private final Ruleset encapsulationOnly =
            new Ruleset(1, Set.of("encapsulation"), Set.of(), Map.of());

    private TriState conceptState(String fixture) {
        AnalysisResult result = engine.run(TestFixtures.facts(fixture), encapsulationOnly);
        assertThat(result.conceptResults()).hasSize(1);
        return result.conceptResults().get(0).state();
    }

    @Test
    void violationWhenEitherSubCheckIsViolated() {
        assertThat(conceptState("encapsulation/violating")).isEqualTo(TriState.VIOLATION);
    }

    @Test
    void satisfiedOnlyWhenBothSubChecksAreProven() {
        assertThat(conceptState("encapsulation/compliant")).isEqualTo(TriState.SATISFIED);
    }

    /** SATISFIED field check AND UNDETERMINED leak check must propagate to UNDETERMINED. */
    @Test
    void undeterminedPropagatesUpward() {
        assertThat(conceptState("encapsulation/ambiguous")).isEqualTo(TriState.UNDETERMINED);
    }
}
