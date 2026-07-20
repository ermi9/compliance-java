import com.acme.external.Gadget; // external type: not JDK-known and not defined in this project

/**
 * Ambiguous: the getter returns a field of a type Praxis cannot classify — it is neither a known JDK
 * type nor defined anywhere in the analyzed project, so its mutability is genuinely unknowable here.
 * The leak check must yield UNDETERMINED, never VIOLATION (invariant 1).
 */
public class AmbiguousHolder {
    private final Gadget gadget;

    public AmbiguousHolder(Gadget gadget) {
        this.gadget = gadget;
    }

    public Gadget getGadget() {
        return gadget; // Gadget mutability is unknowable -> cannot prove a leak
    }
}
