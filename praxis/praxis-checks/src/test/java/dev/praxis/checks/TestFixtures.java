package dev.praxis.checks;

import dev.praxis.core.facts.FactModel;
import dev.praxis.core.index.ProjectIndex;
import java.nio.file.Path;

/** Test helper: resolves the shared fixture corpus and builds a {@link FactModel} over a subtree. */
final class TestFixtures {

    private TestFixtures() {
    }

    static Path root() {
        String dir = System.getProperty("praxis.fixtures.dir");
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("praxis.fixtures.dir system property is not set");
        }
        return Path.of(dir);
    }

    static Path path(String relative) {
        return root().resolve(relative);
    }

    static FactModel facts(String relative) {
        return ProjectIndex.buildUnchecked(path(relative)).factModel();
    }
}
