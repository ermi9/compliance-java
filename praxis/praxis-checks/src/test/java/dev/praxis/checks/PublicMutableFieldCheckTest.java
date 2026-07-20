package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.model.TriState;
import org.junit.jupiter.api.Test;

class PublicMutableFieldCheckTest {

    private final PublicMutableFieldCheck check = new PublicMutableFieldCheck();

    @Test
    void flagsPublicMutableFieldOnViolatingFixture() {
        CheckReport report = check.evaluate(TestFixtures.facts("encapsulation/violating"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.VIOLATION);
        assertThat(report.findings())
                .anySatisfy(f -> {
                    assertThat(f.state()).isEqualTo(TriState.VIOLATION);
                    assertThat(f.explanation()).contains("balance");
                    assertThat(f.line()).isPositive();
                });
    }

    @Test
    void passesCompliantFixture() {
        CheckReport report = check.evaluate(TestFixtures.facts("encapsulation/compliant"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.SATISFIED);
        assertThat(report.findings()).isEmpty();
    }
}
