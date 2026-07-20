package dev.praxis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** End-to-end CLI wiring: distinct exit codes for clean / violation / execution error. */
class CliExitCodeTest {

    private static Path fixture(String rel) {
        return Path.of(System.getProperty("praxis.fixtures.dir")).resolve(rel);
    }

    private static int run(StringWriter out, String... args) {
        CommandLine cmd = new CommandLine(new PraxisCli());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(new StringWriter()));
        return cmd.execute(args);
    }

    private final Path rules = fixture("sample.yml");

    @Test
    void cleanSubmissionExitsZero() {
        StringWriter out = new StringWriter();
        int code = run(out, "check", fixture("encapsulation/compliant").toString(), "--rules", rules.toString());
        assertThat(code).isEqualTo(0);
        assertThat(out.toString()).contains("Verdict: SATISFIED");
    }

    @Test
    void violationExitsOne() {
        StringWriter out = new StringWriter();
        int code = run(out, "check", fixture("encapsulation/violating").toString(), "--rules", rules.toString());
        assertThat(code).isEqualTo(1);
        assertThat(out.toString()).contains("Verdict: VIOLATION");
    }

    @Test
    void missingSubmissionPathExitsTwo() {
        StringWriter out = new StringWriter();
        int code = run(out, "check", fixture("does/not/exist").toString(), "--rules", rules.toString());
        assertThat(code).isEqualTo(2);
    }

    @Test
    void undeterminedOnlySubmissionExitsZero() {
        StringWriter out = new StringWriter();
        int code = run(out, "check", fixture("encapsulation/ambiguous").toString(), "--rules", rules.toString());
        assertThat(code).isEqualTo(0);
        assertThat(out.toString()).contains("Verdict: UNDETERMINED");
    }
}
