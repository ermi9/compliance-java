package dev.praxis.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Praxis CLI entrypoint. Runs with nothing but the JRE and the built jars — no service, DB, or broker
 * (invariant 2 / static-only). Exit codes: {@code 0} clean, {@code 1} violation(s), {@code 2}
 * execution/usage error.
 */
@Command(
        name = "praxis",
        mixinStandardHelpOptions = true,
        version = "praxis 0.1.0",
        description = "Static analyzer for Java student submissions.",
        subcommands = {CheckCommand.class})
public final class PraxisCli {

    static final int EXIT_OK = 0;
    static final int EXIT_VIOLATIONS = 1;
    static final int EXIT_ERROR = 2;

    public static void main(String[] args) {
        int code = new CommandLine(new PraxisCli())
                .setExitCodeExceptionMapper(t -> EXIT_ERROR)
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println("praxis: " + ex.getMessage());
                    return EXIT_ERROR;
                })
                .execute(args);
        System.exit(code);
    }
}
