package dev.praxis.core.facts;

import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;

/**
 * A neutral fact about a declared method. {@code erasedParamTypeNames} holds erased (generics
 * and package stripped) parameter type names for override matching. The underlying
 * {@link MethodDeclaration} node is retained so body-shape checks (e.g. "getter returns a
 * field directly") can inspect the source without re-parsing; it is opaque data, not a verdict.
 */
public record MethodFact(
        String name,
        String ownerQualifiedName,
        List<String> erasedParamTypeNames,
        List<String> typeParameters,
        String returnTypeName,
        String returnSimpleTypeName,
        boolean returnIsVoid,
        boolean returnIsPrimitive,
        boolean returnIsArray,
        boolean isStatic,
        boolean isPrivate,
        boolean isPublic,
        boolean isAbstract,
        SourceRef source,
        MethodDeclaration node) {

    public int parameterCount() {
        return erasedParamTypeNames.size();
    }

    /** Declares one or more method-level type parameters (a generic method). */
    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }

    /** The erased signature used for override/overload matching: {@code name(paramType,...)}. */
    public String erasedSignature() {
        return name + "(" + String.join(",", erasedParamTypeNames) + ")";
    }
}
