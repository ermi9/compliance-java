package dev.praxis.core.facts;

/**
 * A neutral fact about a method call site: the method name invoked and the <em>declared static
 * type</em> of its receiver (as best resolved), plus where it occurs.
 *
 * <p>This type exists so the Layer-1 model can <em>hold</em> the data the future dynamic-dispatch
 * / type-flow analysis will need. Per this session's scope, no dispatch reasoning is implemented
 * over it and population is best-effort — a call site whose receiver type cannot be resolved
 * stores {@code null} for {@code receiverDeclaredType} rather than guessing.
 */
public record CallSiteFact(
        String methodName,
        String receiverDeclaredType,
        String enclosingTypeQualifiedName,
        SourceRef source) {
}
