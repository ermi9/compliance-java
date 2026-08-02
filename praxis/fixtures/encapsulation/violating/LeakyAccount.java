import java.util.ArrayList;
import java.util.List;

/** Violating: a public mutable field AND a getter that leaks an internal mutable collection. */
public class LeakyAccount {
    public int balance;                          // public, non-final instance field -> representation leak
    private final List<String> log = new ArrayList<>();

    public List<String> getLog() {
        return log;                              // returns the internal List by reference -> leak
    }
}
