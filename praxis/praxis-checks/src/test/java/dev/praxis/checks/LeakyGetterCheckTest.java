package dev.praxis.checks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.model.TriState;
import org.junit.jupiter.api.Test;

class LeakyGetterCheckTest {

    private final LeakyGetterCheck check = new LeakyGetterCheck();

    @Test
    void flagsGetterThatReturnsInternalMutableCollection() {
        CheckReport report = check.evaluate(TestFixtures.facts("encapsulation/violating"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.VIOLATION);
        assertThat(report.findings())
                .anySatisfy(f -> {
                    assertThat(f.state()).isEqualTo(TriState.VIOLATION);
                    assertThat(f.explanation()).contains("getLog");
                });
    }

    @Test
    void passesGetterThatReturnsDefensiveCopyOrImmutable() {
        CheckReport report = check.evaluate(TestFixtures.facts("encapsulation/compliant"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.SATISFIED);
        assertThat(report.findings()).isEmpty();
    }

    /** Invariant 1: an unprovable case must be UNDETERMINED, never VIOLATION. */
    @Test
    void ambiguousReturnYieldsUndeterminedNotViolation() {
        CheckReport report = check.evaluate(TestFixtures.facts("encapsulation/ambiguous"), CheckOptions.empty());

        assertThat(report.overall()).isEqualTo(TriState.UNDETERMINED);
        assertThat(report.findings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.state()).isNotEqualTo(TriState.VIOLATION));
    }
}
