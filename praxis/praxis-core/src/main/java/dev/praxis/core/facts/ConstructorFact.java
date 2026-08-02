package dev.praxis.core.facts;

import java.util.List;

/**
 * A neutral fact about a declared constructor. {@code erasedParamTypeNames} supports overloading
 * detection (two constructors, same owner, different erased signatures).
 */
public record ConstructorFact(
        String ownerQualifiedName,
        List<String> erasedParamTypeNames,
        boolean isPrivate,
        SourceRef source) {

    public int parameterCount() {
        return erasedParamTypeNames.size();
    }
}
