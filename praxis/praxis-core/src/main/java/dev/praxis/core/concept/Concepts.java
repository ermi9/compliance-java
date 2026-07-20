package dev.praxis.core.concept;

import dev.praxis.core.check.CheckIds;
import java.util.List;
import java.util.Optional;

/**
 * The built-in Layer-3 concept definitions.
 *
 * <p>Only definitions that do not depend on course wording still under review are declared here.
 * The four polymorphism concepts and the information-hiding three-step concept are intentionally
 * <b>omitted</b> this session: their final definitions must be validated with the professor and must
 * not be hard-coded prematurely (see CLAUDE.md, "Deferred").
 */
public final class Concepts {

    private Concepts() {
    }

    /**
     * {@code Encapsulation = (no public mutable field) ∧ (no leaked mutable internal)}.
     *
     * <p>A safe, definition-stable composition used to prove Layer-3 AND-composition and
     * {@code UNDETERMINED}-propagation end to end. If either sub-check is {@code UNDETERMINED}, the
     * concept is {@code UNDETERMINED}; a proven leak or public mutable field makes it {@code VIOLATION};
     * it is {@code SATISFIED} only when both are proven.
     */
    public static final Concept ENCAPSULATION = new Concept(
            "encapsulation",
            "Encapsulation = no public mutable field AND no leaked mutable internal reference",
            BoolExpr.and(
                    BoolExpr.check(CheckIds.NO_PUBLIC_MUTABLE_FIELD),
                    BoolExpr.check(CheckIds.NO_LEAKED_MUTABLE_INTERNAL)));

    /** All built-in concepts. */
    public static List<Concept> builtIn() {
        return List.of(ENCAPSULATION);
    }

    /** Looks up a built-in concept by id. */
    public static Optional<Concept> byId(String id) {
        return builtIn().stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
