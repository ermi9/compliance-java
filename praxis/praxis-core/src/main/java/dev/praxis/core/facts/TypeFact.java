package dev.praxis.core.facts;

import java.util.List;

/**
 * A neutral fact about a declared reference type and the members Praxis has extracted from it.
 * Layer 1 is fact-only: no verdicts. {@code extendedTypeNames} / {@code implementedTypeNames}
 * are as written (for hierarchy reasoning); {@code resolved} records whether the symbol solver
 * could bind this type, so dependent checks can yield {@code UNDETERMINED} when it could not.
 */
public record TypeFact(
        String qualifiedName,
        String simpleName,
        Kind kind,
        boolean isAbstract,
        List<String> typeParameters,
        List<String> extendedTypeNames,
        List<String> implementedTypeNames,
        List<FieldFact> fields,
        List<MethodFact> methods,
        List<ConstructorFact> constructors,
        boolean resolved,
        SourceRef source) {

    public enum Kind {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        ANNOTATION
    }

    /** Number of directly declared interfaces this type implements. */
    public int implementedInterfaceCount() {
        return implementedTypeNames.size();
    }

    /** An interface, or a class explicitly declared {@code abstract}: an abstraction seam. */
    public boolean isAbstraction() {
        return kind == Kind.INTERFACE || (kind == Kind.CLASS && isAbstract);
    }

    /** Declares one or more type parameters (a generic type): parametric-polymorphism evidence. */
    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }
}
