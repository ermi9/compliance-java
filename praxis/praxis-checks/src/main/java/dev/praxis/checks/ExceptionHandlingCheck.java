package dev.praxis.checks;

import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypeHierarchy;
import dev.praxis.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * Exception handling: the project declares a custom exception type (a class in the {@code Exception} /
 * {@code Error} / {@code Throwable} lineage). The evidence explanation also reports project-wide
 * {@code throw} / {@code catch} / {@code throws} usage counts. Absence of a custom exception type is
 * decidable from complete declarations.
 */
public final class ExceptionHandlingCheck extends DemonstrationCheck {

    @Override
    public String id() {
        return CheckIds.EXCEPTION_HANDLING;
    }

    @Override
    public String description() {
        return "A custom exception type is declared and exceptions are used.";
    }

    @Override
    protected List<Finding> findEvidence(FactModel facts, TypeHierarchy hierarchy, CheckOptions options) {
        String usage = " (project usage: " + facts.throwStatementCount() + " throw, "
                + facts.catchClauseCount() + " catch, " + facts.throwsDeclarationCount() + " throws)";
        List<Finding> evidence = new ArrayList<>();
        for (TypeFact t : facts.types()) {
            if (t.kind() != TypeFact.Kind.CLASS) {
                continue;
            }
            if (looksLikeException(t.simpleName()) || t.extendedTypeNames().stream().anyMatch(ExceptionHandlingCheck::looksLikeException)
                    || hierarchy.ancestorNamesOf(t).stream().anyMatch(ExceptionHandlingCheck::looksLikeException)) {
                evidence.add(evidence(t.source(),
                        "Exception handling: custom exception type '" + t.simpleName() + "'" + usage + "."));
            }
        }
        return evidence;
    }

    private static boolean looksLikeException(String name) {
        return name.endsWith("Exception") || name.endsWith("Error") || name.equals("Throwable");
    }

    @Override
    protected boolean absenceIsDecidable() {
        return true;
    }
}
