package dev.praxis.core.engine;

import dev.praxis.core.check.Check;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.concept.Concept;
import dev.praxis.core.concept.ConceptEvaluator;
import dev.praxis.core.concept.ConceptResult;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.model.Finding;
import dev.praxis.core.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Orchestrates the three layers: takes the Layer-1 {@link FactModel} and the professor's
 * {@link Ruleset}, runs the selected Layer-2 checks, evaluates the selected Layer-3 concepts, and
 * assembles a deterministic {@link AnalysisResult}.
 *
 * <p>The engine is framework-free and takes its checks and concepts as inputs, so core never needs
 * to depend on the concrete checks (which live in praxis-checks).
 */
public final class Engine {

    private final List<Check> availableChecks;
    private final List<Concept> availableConcepts;

    public Engine(List<Check> availableChecks, List<Concept> availableConcepts) {
        this.availableChecks = List.copyOf(availableChecks);
        this.availableConcepts = List.copyOf(availableConcepts);
    }

    public AnalysisResult run(FactModel facts, Ruleset ruleset) {
        Map<String, Check> checksById = new LinkedHashMap<>();
        for (Check c : availableChecks) {
            checksById.put(c.id(), c);
        }

        List<Concept> enabledConcepts = availableConcepts.stream()
                .filter(c -> ruleset.enabledConceptIds().contains(c.id()))
                .sorted(Comparator.comparing(Concept::id))
                .toList();

        // Run every explicitly-enabled check plus every check any enabled concept references.
        TreeSet<String> checkIdsToRun = new TreeSet<>(ruleset.enabledCheckIds());
        for (Concept concept : enabledConcepts) {
            checkIdsToRun.addAll(concept.referencedCheckIds());
        }

        Map<String, CheckReport> reports = new LinkedHashMap<>();
        for (String id : checkIdsToRun) {
            Check check = checksById.get(id);
            if (check == null) {
                // Selected but no implementation available: leave absent so concepts see UNDETERMINED.
                continue;
            }
            reports.put(id, check.evaluate(facts, ruleset.optionsFor(id)));
        }

        List<ConceptResult> conceptResults = new ArrayList<>();
        for (Concept concept : enabledConcepts) {
            conceptResults.add(ConceptEvaluator.evaluate(concept, reports));
        }

        List<Finding> allFindings = new ArrayList<>();
        for (CheckReport report : reports.values()) {
            allFindings.addAll(report.findings());
        }
        allFindings.sort(null);

        List<CheckReport> sortedReports = reports.values().stream()
                .sorted(Comparator.comparing(CheckReport::checkId))
                .toList();

        return new AnalysisResult(sortedReports, conceptResults, allFindings, facts.unparsableFiles());
    }
}
