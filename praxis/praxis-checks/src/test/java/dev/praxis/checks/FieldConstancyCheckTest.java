package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.model.TriState;
import org.junit.jupiter.api.Test;

/** The constancy step of information hiding: never-reassigned fields must be final. */
class FieldConstancyCheckTest {

    private final FieldConstancyCheck check = new FieldConstancyCheck();

    @Test
    void flagsNeverReassignedNonFinalField() {
        CheckReport report = check.evaluate(TestFixtures.facts("information-hiding/bad"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.VIOLATION);
        assertThat(report.findings())
                .anySatisfy(f -> {
                    assertThat(f.state()).isEqualTo(TriState.VIOLATION);
                    assertThat(f.explanation()).contains("'id'").contains("final");
                });
        // The genuinely-mutated 'name' field must NOT be flagged.
        assertThat(report.findings()).noneSatisfy(f -> assertThat(f.explanation()).contains("'name'"));
    }

    @Test
    void passesWhenConstancyIsCorrect() {
        CheckReport report = check.evaluate(TestFixtures.facts("information-hiding/good"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.SATISFIED);
        assertThat(report.findings()).isEmpty();
    }
}
