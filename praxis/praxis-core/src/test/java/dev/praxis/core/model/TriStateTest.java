package dev.praxis.core.model;

import static dev.praxis.core.model.TriState.SATISFIED;
import static dev.praxis.core.model.TriState.UNDETERMINED;
import static dev.praxis.core.model.TriState.VIOLATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TriStateTest {

    @Test
    void andViolationDominatesSoProvenEvidenceIsNeverMasked() {
        assertThat(VIOLATION.and(UNDETERMINED)).isEqualTo(VIOLATION);
        assertThat(UNDETERMINED.and(VIOLATION)).isEqualTo(VIOLATION);
        assertThat(VIOLATION.and(SATISFIED)).isEqualTo(VIOLATION);
    }

    @Test
    void andUndeterminedPropagatesWhenNoViolation() {
        assertThat(SATISFIED.and(UNDETERMINED)).isEqualTo(UNDETERMINED);
        assertThat(SATISFIED.and(SATISFIED)).isEqualTo(SATISFIED);
    }

    @Test
    void orSatisfiedDominates() {
        assertThat(VIOLATION.or(SATISFIED)).isEqualTo(SATISFIED);
        assertThat(UNDETERMINED.or(SATISFIED)).isEqualTo(SATISFIED);
        assertThat(VIOLATION.or(UNDETERMINED)).isEqualTo(UNDETERMINED);
        assertThat(VIOLATION.or(VIOLATION)).isEqualTo(VIOLATION);
    }

    @Test
    void notSwapsVerdictsButLeavesUnknown() {
        assertThat(SATISFIED.not()).isEqualTo(VIOLATION);
        assertThat(VIOLATION.not()).isEqualTo(SATISFIED);
        assertThat(UNDETERMINED.not()).isEqualTo(UNDETERMINED);
    }

    @Test
    void foldsHaveExpectedIdentities() {
        assertThat(TriState.allOf(List.of())).isEqualTo(SATISFIED);
        assertThat(TriState.anyOf(List.of())).isEqualTo(VIOLATION);
        assertThat(TriState.allOf(List.of(SATISFIED, SATISFIED, VIOLATION))).isEqualTo(VIOLATION);
        assertThat(TriState.allOf(List.of(SATISFIED, UNDETERMINED))).isEqualTo(UNDETERMINED);
    }
}
