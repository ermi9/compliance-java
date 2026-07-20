package dev.praxis.checks;

import java.util.Set;

/**
 * Conservative, name-based classification of a field's declared type as mutable, immutable, or
 * unknown. Deliberately errs toward {@link Mutability#UNKNOWN}: only well-known JDK types are
 * classified, so a custom or unresolved type yields {@code UNKNOWN} and the leak check falls back to
 * {@code UNDETERMINED} rather than guessing (invariant 1).
 *
 * <p>This is a heuristic input to a check, not a verdict; it never sees submission configuration.
 */
final class MutabilityHeuristics {

    enum Mutability { MUTABLE, IMMUTABLE, UNKNOWN }

    // Well-known mutable JDK reference types (simple names). Arrays are handled separately.
    private static final Set<String> MUTABLE = Set.of(
            "List", "ArrayList", "LinkedList", "Vector", "Stack",
            "Map", "HashMap", "TreeMap", "LinkedHashMap", "Hashtable",
            "Set", "HashSet", "TreeSet", "LinkedHashSet",
            "Collection", "Queue", "Deque", "ArrayDeque", "PriorityQueue",
            "Date", "Calendar", "StringBuilder", "StringBuffer");

    // Well-known immutable / value types (simple names) that are safe to return by reference.
    private static final Set<String> IMMUTABLE = Set.of(
            "String", "Integer", "Long", "Short", "Byte", "Boolean", "Character",
            "Double", "Float", "Number", "BigInteger", "BigDecimal",
            "LocalDate", "LocalDateTime", "LocalTime", "Instant", "ZonedDateTime",
            "OffsetDateTime", "Duration", "Period", "UUID", "Class");

    private MutabilityHeuristics() {
    }

    static Mutability classify(String simpleTypeName, boolean isArray) {
        if (isArray) {
            return Mutability.MUTABLE; // arrays are always mutable and always alias
        }
        if (MUTABLE.contains(simpleTypeName)) {
            return Mutability.MUTABLE;
        }
        if (IMMUTABLE.contains(simpleTypeName)) {
            return Mutability.IMMUTABLE;
        }
        return Mutability.UNKNOWN;
    }
}
