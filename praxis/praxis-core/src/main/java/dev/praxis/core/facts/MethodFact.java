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
}
