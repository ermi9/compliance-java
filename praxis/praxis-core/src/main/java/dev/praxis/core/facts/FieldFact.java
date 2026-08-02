package dev.praxis.core.facts;

/**
 * A neutral fact about a single declared field. Modifiers come straight from the AST (always
 * reliable when the file parsed); {@code declaredTypeName} is the type exactly as written, and
 * {@code simpleTypeName} is that with any package qualifier and generic arguments stripped.
 * No verdicts here (Layer 1 is fact-only).
 */
public record FieldFact(
        String name,
        String ownerQualifiedName,
        boolean isPrivate,
        boolean isProtected,
        boolean isPublic,
        boolean isPackagePrivate,
        boolean isStatic,
        boolean isFinal,
        boolean isArray,
        boolean reassignable,
        boolean assignmentsFullyVisible,
        String declaredTypeName,
        String simpleTypeName,
        SourceRef source) {

    /** True for a field that belongs to instances (not a static/class field). */
    public boolean isInstanceField() {
        return !isStatic;
    }

    /**
     * True when the field is ever reassigned or mutated in place after construction (assigned in a
     * method, or the target of a compound/unary operator anywhere). Used by the constancy step of
     * information hiding: a field that is NOT reassignable could and should be declared {@code final}.
     */
    public boolean isReassignable() {
        return reassignable;
    }

    /**
     * True when Praxis can see every assignment to this field (its declaring type is top-level, so a
     * {@code private} field's assignments are wholly contained). When false, constancy is left
     * UNDETERMINED rather than guessed.
     */
    public boolean assignmentsFullyVisible() {
        return assignmentsFullyVisible;
    }
}
