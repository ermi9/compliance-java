package dev.praxis.cli;

import dev.praxis.core.ruleset.Ruleset;
import dev.praxis.core.ruleset.RulesetLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * YAML implementation of {@link RulesetLoader}. Lives in the CLI so praxis-core stays framework- and
 * dependency-light (invariant 3). Uses SnakeYAML's {@link SafeConstructor}, so a ruleset file can
 * never instantiate arbitrary Java types.
 *
 * <p>Accepted schema:
 * <pre>
 * version: 1
 * concepts: [encapsulation]
 * checks:
 *   - id: field.no-public-mutable
 *   - id: method.getter-leaks-internal
 *     options: { key: value }
 * </pre>
 */
public final class YamlRulesetLoader implements RulesetLoader {

    @Override
    public Ruleset load(Path file) throws IOException {
        Object root;
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            root = yaml.load(in);
        }
        if (root == null) {
            throw new InvalidRulesetException("ruleset file is empty: " + file);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new InvalidRulesetException("ruleset root must be a mapping, got: " + root.getClass().getSimpleName());
        }

        int version = intValue(map.get("version"), 1);
        Set<String> concepts = new LinkedHashSet<>();
        Set<String> checks = new LinkedHashSet<>();
        Map<String, Map<String, Object>> options = new LinkedHashMap<>();

        for (Object c : asList(map.get("concepts"), "concepts")) {
            concepts.add(String.valueOf(c));
        }

        for (Object c : asList(map.get("checks"), "checks")) {
            if (c instanceof String s) {
                checks.add(s);
            } else if (c instanceof Map<?, ?> cm) {
                Object id = cm.get("id");
                if (id == null) {
                    throw new InvalidRulesetException("each check entry needs an 'id'");
                }
                String checkId = String.valueOf(id);
                checks.add(checkId);
                if (cm.get("options") instanceof Map<?, ?> opt) {
                    Map<String, Object> typed = new LinkedHashMap<>();
                    opt.forEach((k, v) -> typed.put(String.valueOf(k), v));
                    options.put(checkId, typed);
                }
            } else {
                throw new InvalidRulesetException("check entries must be a string id or a mapping");
            }
        }

        return new Ruleset(version, concepts, checks, options);
    }

    private static List<?> asList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        throw new InvalidRulesetException("'" + field + "' must be a list");
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
