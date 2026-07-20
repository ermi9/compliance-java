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
        String declaredTypeName,
        String simpleTypeName,
        SourceRef source) {

    /** True for a field that belongs to instances (not a static/class field). */
    public boolean isInstanceField() {
        return !isStatic;
    }
}
