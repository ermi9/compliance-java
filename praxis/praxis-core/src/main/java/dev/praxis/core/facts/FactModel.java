package dev.praxis.core.facts;

import java.util.List;

/**
 * The immutable Layer-1 output: every fact extracted once from the parsed project, in a neutral
 * form with no verdicts. Checks (Layer 2) read from here and never re-parse.
 *
 * <p>Collections are returned in a deterministic order (types sorted by qualified name) so that
 * downstream iteration cannot leak hash-map order into output (invariant 4).
 */
public record FactModel(
        List<TypeFact> types,
        List<CallSiteFact> callSites,
        List<TypedNewFact> typedNews,
        int throwStatementCount,
        int catchClauseCount,
        int throwsDeclarationCount,
        List<String> unparsableFiles) {

    /** All types declared in files that parsed successfully, sorted by qualified name. */
    public List<TypeFact> types() {
        return types;
    }

    /** Files that failed to parse; dependent checks over their would-be types yield UNDETERMINED. */
    public List<String> unparsableFiles() {
        return unparsableFiles;
    }

    /** True when the whole corpus parsed, so "concept provably absent" verdicts are defensible. */
    public boolean fullyParsed() {
        return unparsableFiles.isEmpty();
    }
}
