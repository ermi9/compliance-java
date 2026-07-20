package dev.praxis.checks;

import dev.praxis.core.check.Check;
import java.util.List;

/** Registry of the atomic checks shipped in this module. */
public final class BuiltInChecks {

    private BuiltInChecks() {
    }

    public static List<Check> all() {
        return List.of(
                // Encapsulation / information hiding (quality checks: VIOLATION on proven defect)
                new PublicMutableFieldCheck(),
                new LeakyGetterCheck(),
                new AllFieldsPrivateCheck(),
                new FieldConstancyCheck(),
                // Concept-demonstration checks (SATISFIED on located evidence)
                new AbstractionCheck(),
                new InheritanceCheck(),
                new SubtypingCheck(),
                new CompositionCheck(),
                new OverloadingCheck(),
                new GenericsCheck(),
                new CoercionCheck(),
                new InclusionDispatchCheck(),
                new ExceptionHandlingCheck(),
                new ExtensibilityCheck());
    }
}
