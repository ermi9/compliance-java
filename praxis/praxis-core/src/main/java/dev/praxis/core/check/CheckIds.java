package dev.praxis.core.check;

/**
 * Canonical atomic-check ids, shared between the check implementations (Layer 2, in praxis-checks)
 * and the concept definitions (Layer 3, here in core) so the two never drift out of sync.
 */
public final class CheckIds {

    private CheckIds() {
    }

    /** A non-private, non-final instance field exposes internal state directly. */
    public static final String NO_PUBLIC_MUTABLE_FIELD = "field.no-public-mutable";

    /** A getter returns a mutable internal reference directly instead of a defensive copy. */
    public static final String NO_LEAKED_MUTABLE_INTERNAL = "method.getter-leaks-internal";

    /** Information hiding — visibility step: every instance field is {@code private}. */
    public static final String ALL_FIELDS_PRIVATE = "field.all-private";

    /** Information hiding — constancy step: a never-reassigned field is declared {@code final}. */
    public static final String FIELD_CONSTANCY = "field.constancy";

    /** Abstraction: an interface or abstract class is declared. */
    public static final String DECLARES_ABSTRACTION = "type.declares-abstraction";

    /** Inheritance: a type extends a supertype. */
    public static final String USES_INHERITANCE = "type.uses-inheritance";

    /** Subtyping / multityping: a type implements one or more interfaces. */
    public static final String IMPLEMENTS_INTERFACE = "type.implements-interface";

    /** Composition: a type has-a field of another project type. */
    public static final String USES_COMPOSITION = "type.uses-composition";

    /** Ad-hoc polymorphism: a method or constructor is overloaded. */
    public static final String OVERLOADS = "method.overloading";

    /** Parametric polymorphism: a generic type or generic method is declared. */
    public static final String DECLARES_GENERIC = "type.declares-generic";

    /** Coercion polymorphism: a subtype instance is bound to a supertype-typed target. */
    public static final String COERCION_UPCAST = "poly.coercion-upcast";

    /** Inclusion polymorphism (provisional): an overridden method is called via a supertype reference. */
    public static final String INCLUSION_DISPATCH = "poly.inclusion-dispatch";

    /** Exception handling: a custom exception type is declared (usage stats reported). */
    public static final String EXCEPTION_HANDLING = "exception.custom-and-usage";

    /** Extensibility (provisional): an abstraction is actually extended/implemented by a subtype. */
    public static final String EXTENSIBILITY = "type.extensibility";
}
