package dev.praxis.core.facts;

/**
 * A neutral, AST-decidable type-flow edge: an object creation ({@code new Created(...)}) whose value
 * is bound to a target of a different declared static type. The upcast check uses this together with
 * the {@link TypeHierarchy} to prove coercion polymorphism (a subtype instance used as a supertype)
 * without needing the full deferred type-flow analysis.
 *
 * @param targetTypeName  declared static type of the binding (variable/field/return), erased simple name
 * @param createdTypeName the instantiated type, erased simple name
 * @param site            where the binding occurs (for evidence)
 * @param source          location
 */
public record TypedNewFact(
        String targetTypeName,
        String createdTypeName,
        Site site,
        SourceRef source) {

    public enum Site { VARIABLE, FIELD, RETURN }
}
