/**
 * Ambiguous: the getter returns a field of a custom type whose mutability Praxis cannot determine.
 * The leak check must yield UNDETERMINED here, never VIOLATION (invariant 1).
 */
public class AmbiguousHolder {
    private final Widget widget = new Widget();

    public Widget getWidget() {
        return widget; // Widget mutability is unknown -> cannot prove a leak
    }
}

class Widget {
    private int x;

    int value() {
        return x;
    }
}
