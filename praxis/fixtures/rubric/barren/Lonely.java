/**
 * A single concrete, immutable class that demonstrates none of the decidable rubric concepts:
 * no abstraction, no inheritance, no interface, no generics, no overloading, no custom exception.
 * Decidable concepts must read VIOLATION here; non-decidable ones (composition/coercion/inclusion/
 * extensibility) must read UNDETERMINED, never VIOLATION.
 */
public class Lonely {
    private final int value;

    public Lonely(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
