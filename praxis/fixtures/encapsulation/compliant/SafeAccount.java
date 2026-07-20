import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compliant: all state is private/final; getters return immutable values or defensive views. */
public class SafeAccount {
    private final String owner;
    private final List<String> transactions = new ArrayList<>();

    public SafeAccount(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner; // String is immutable -> safe to return by reference
    }

    public List<String> getTransactions() {
        return Collections.unmodifiableList(transactions); // defensive view -> safe
    }
}
