package dev.praxis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.checks.BuiltInChecks;
import dev.praxis.core.concept.Concepts;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.engine.Engine;
import dev.praxis.core.index.ProjectIndex;
import dev.praxis.core.ruleset.Ruleset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Invariant 4: same input bytes + same ruleset ⇒ byte-identical output across independent runs. */
class DeterminismTest {

    private static Path fixture(String rel) {
        return Path.of(System.getProperty("praxis.fixtures.dir")).resolve(rel);
    }

    private static AnalysisResult analyze(String fixture) {
        Engine engine = new Engine(BuiltInChecks.all(), Concepts.builtIn());
        Ruleset ruleset = new Ruleset(1, Set.of("encapsulation"), Set.of(), Map.of());
        return engine.run(ProjectIndex.buildUnchecked(fixture(fixture)).factModel(), ruleset);
    }

    @Test
    void textOutputIsByteIdenticalAcrossRuns() {
        byte[] first = TextReportWriter.render(analyze("encapsulation/violating")).getBytes(StandardCharsets.UTF_8);
        byte[] second = TextReportWriter.render(analyze("encapsulation/violating")).getBytes(StandardCharsets.UTF_8);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void jsonOutputIsByteIdenticalAcrossRuns() {
        byte[] first = JsonReportWriter.render(analyze("encapsulation/violating")).getBytes(StandardCharsets.UTF_8);
        byte[] second = JsonReportWriter.render(analyze("encapsulation/violating")).getBytes(StandardCharsets.UTF_8);
        assertThat(second).isEqualTo(first);
    }
}
