package dev.praxis.core.concept;

import dev.praxis.core.check.CheckIds;
import java.util.List;
import java.util.Optional;

/**
 * The built-in Layer-3 concept definitions — one per OOP-rubric requirement.
 *
 * <p>Definitions marked <b>(provisional)</b> encode a sound, reasonable reading of a rubric item whose
 * exact wording still needs professor sign-off (information hiding's "three-step" definition, and the
 * polymorphism kinds). They are composed only from atomic checks, so a professor can retune them by
 * editing the composition here or selecting different checks in the ruleset — never by touching logic.
 *
 * <p>Semantics reminder: a demonstration concept is {@code SATISFIED} only with located evidence,
 * {@code VIOLATION} only when provably absent across fully-parsed code, else {@code UNDETERMINED}.
 */
public final class Concepts {

    private Concepts() {
    }

    /** Encapsulation = no public mutable field AND no leaked mutable internal reference. */
    public static final Concept ENCAPSULATION = new Concept(
            "encapsulation",
            "Encapsulation = no public mutable field AND no leaked mutable internal reference",
            BoolExpr.and(
                    BoolExpr.check(CheckIds.NO_PUBLIC_MUTABLE_FIELD),
                    BoolExpr.check(CheckIds.NO_LEAKED_MUTABLE_INTERNAL)));

    /** Information hiding (provisional) = all instance fields private AND no leaked mutable internal. */
    public static final Concept INFORMATION_HIDING = new Concept(
            "information_hiding",
            "Information hiding (provisional) = all instance fields private AND no leaked internal state",
            BoolExpr.and(
                    BoolExpr.check(CheckIds.ALL_FIELDS_PRIVATE),
                    BoolExpr.check(CheckIds.NO_LEAKED_MUTABLE_INTERNAL)));

    /** Inheritance = a type extends a supertype. */
    public static final Concept INHERITANCE = new Concept(
            "inheritance", "Inheritance = a type extends a supertype",
            BoolExpr.check(CheckIds.USES_INHERITANCE));

    /** Composition = a type has-a field of another project type. */
    public static final Concept COMPOSITION = new Concept(
            "composition", "Composition = a type has-a field of another project type",
            BoolExpr.check(CheckIds.USES_COMPOSITION));

    /** Abstraction = an interface or abstract class is declared. */
    public static final Concept ABSTRACTION = new Concept(
            "abstraction", "Abstraction = an interface or abstract class is declared",
            BoolExpr.check(CheckIds.DECLARES_ABSTRACTION));

    /** Subtyping / multityping = a type implements one or more interfaces. */
    public static final Concept SUBTYPING = new Concept(
            "subtyping", "Subtyping / multityping = a type implements one or more interfaces",
            BoolExpr.check(CheckIds.IMPLEMENTS_INTERFACE));

    /** Ad-hoc polymorphism = a method or constructor is overloaded. */
    public static final Concept POLYMORPHISM_OVERLOADING = new Concept(
            "polymorphism_overloading", "Ad-hoc polymorphism = a method or constructor is overloaded",
            BoolExpr.check(CheckIds.OVERLOADS));

    /** Parametric polymorphism = a generic type or generic method is declared. */
    public static final Concept POLYMORPHISM_PARAMETRIC = new Concept(
            "polymorphism_parametric", "Parametric polymorphism = a generic type or method is declared",
            BoolExpr.check(CheckIds.DECLARES_GENERIC));

    /** Coercion polymorphism = a subtype instance is used as a supertype. */
    public static final Concept POLYMORPHISM_COERCION = new Concept(
            "polymorphism_coercion", "Coercion polymorphism = a subtype instance is used as a supertype",
            BoolExpr.check(CheckIds.COERCION_UPCAST));

    /** Inclusion polymorphism (provisional) = an overridden method is called via a supertype reference. */
    public static final Concept POLYMORPHISM_INCLUSION = new Concept(
            "polymorphism_inclusion",
            "Inclusion polymorphism (provisional) = overridden method invoked through a supertype reference",
            BoolExpr.check(CheckIds.INCLUSION_DISPATCH));

    /** Exception handling = a custom exception type is declared (and exceptions are used). */
    public static final Concept EXCEPTION_HANDLING = new Concept(
            "exception_handling", "Exception handling = a custom exception type is declared and used",
            BoolExpr.check(CheckIds.EXCEPTION_HANDLING));

    /** Extensibility (provisional) = an abstraction is extended/implemented by a subtype. */
    public static final Concept EXTENSIBILITY = new Concept(
            "extensibility", "Extensibility (provisional) = an abstraction is extended/implemented by a subtype",
            BoolExpr.check(CheckIds.EXTENSIBILITY));

    /** All built-in concepts, in a stable order. */
    public static List<Concept> builtIn() {
        return List.of(
                ENCAPSULATION,
                INFORMATION_HIDING,
                INHERITANCE,
                COMPOSITION,
                ABSTRACTION,
                SUBTYPING,
                POLYMORPHISM_OVERLOADING,
                POLYMORPHISM_PARAMETRIC,
                POLYMORPHISM_COERCION,
                POLYMORPHISM_INCLUSION,
                EXCEPTION_HANDLING,
                EXTENSIBILITY);
    }

    /** Looks up a built-in concept by id. */
    public static Optional<Concept> byId(String id) {
        return builtIn().stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
