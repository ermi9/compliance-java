package dev.praxis.cli;

import dev.praxis.checks.BuiltInChecks;
import dev.praxis.core.concept.Concepts;
import dev.praxis.core.engine.AnalysisResult;
import dev.praxis.core.engine.Engine;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.index.ProjectIndex;
import dev.praxis.core.ruleset.Ruleset;
import dev.praxis.core.ruleset.RulesetLoader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * {@code praxis check <path> --rules <file>}: parses a submission, runs the ruleset-selected checks
 * and concepts, and prints findings plus a verdict. Never executes submission code.
 */
@Command(
        name = "check",
        mixinStandardHelpOptions = true,
        description = "Analyze a submission against a professor ruleset.")
public final class CheckCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PATH", description = "Submission root (a directory tree or a single .java file).")
    Path path;

    @Option(names = "--rules", required = true, paramLabel = "FILE", description = "Ruleset YAML file.")
    Path rules;

    @Option(names = "--format", paramLabel = "FORMAT", defaultValue = "text", description = "Output format: text or json.")
    String format;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.exists(path)) {
            err.println("praxis: submission path does not exist: " + path);
            return PraxisCli.EXIT_ERROR;
        }
        if (!Files.exists(rules)) {
            err.println("praxis: ruleset file does not exist: " + rules);
            return PraxisCli.EXIT_ERROR;
        }
        String fmt = format.toLowerCase();
        if (!fmt.equals("text") && !fmt.equals("json")) {
            err.println("praxis: unknown --format '" + format + "' (expected text or json)");
            return PraxisCli.EXIT_ERROR;
        }

        Ruleset ruleset;
        try {
            RulesetLoader loader = new YamlRulesetLoader();
            ruleset = loader.load(rules);
        } catch (IOException | RulesetLoader.InvalidRulesetException e) {
            err.println("praxis: could not load ruleset: " + e.getMessage());
            return PraxisCli.EXIT_ERROR;
        }

        FactModel facts;
        try {
            facts = ProjectIndex.build(path).factModel();
        } catch (IOException e) {
            err.println("praxis: could not read submission: " + e.getMessage());
            return PraxisCli.EXIT_ERROR;
        }

        Engine engine = new Engine(BuiltInChecks.all(), Concepts.builtIn());
        AnalysisResult result = engine.run(facts, ruleset);

        out.print(fmt.equals("json") ? JsonReportWriter.render(result) : TextReportWriter.render(result));
        out.flush();

        return result.hasViolation() ? PraxisCli.EXIT_VIOLATIONS : PraxisCli.EXIT_OK;
    }
}
