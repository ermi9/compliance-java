package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.concept.Concepts;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.engine.Engine;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import dev.praxis.core.ruleset.Ruleset;

/**
 * Invariant 6: the submission is data, never configuration. A submission with misleading class names,
 * fake "compliant" annotations, and a lying comment must grade identically to a benign-named
 * submission with the same structure.
 */
class AdversarialInputTest {

    private final Engine engine = new Engine(BuiltInChecks.all(), Concepts.builtIn());
    private final Ruleset ruleset = new Ruleset(1, Set.of("encapsulation"), Set.of(), Map.of());

    @Test
    void disguisedSubmissionGradesIdenticallyToBenignEquivalent() {
        AnalysisResult benign = engine.run(TestFixtures.facts("adversarial/benign"), ruleset);
        AnalysisResult disguised = engine.run(TestFixtures.facts("adversarial/disguised"), ruleset);

        // Same verdict and same amount of evidence, regardless of names/annotations/comments.
        assertThat(disguised.overallState()).isEqualTo(benign.overallState());
        assertThat(disguised.findings()).hasSameSizeAs(benign.findings());
        assertThat(disguised.conceptResults().get(0).state())
                .isEqualTo(benign.conceptResults().get(0).state());
    }
}
