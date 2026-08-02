package dev.praxis.core.ruleset;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads a {@link Ruleset} from an external source. The interface lives in core so core stays
 * format-agnostic and framework-free (invariant 3); the concrete YAML implementation lives in the
 * CLI module with its YAML dependency.
 */
public interface RulesetLoader {

    /**
     * @throws IOException              if the file cannot be read
     * @throws InvalidRulesetException  if the content is not a valid ruleset
     */
    Ruleset load(Path file) throws IOException;

    /** Thrown when a ruleset file is syntactically or structurally invalid. */
    class InvalidRulesetException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidRulesetException(String message) {
            super(message);
        }

        public InvalidRulesetException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
