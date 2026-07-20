/**
 * Constancy violation: 'id' is private and never reassigned after construction, so it should be
 * declared final. 'name' IS reassigned (in rename), so leaving it non-final is correct.
 */
public class ConstancyBad {
    private int id;      // never reassigned -> should be final -> VIOLATION
    private String name; // reassigned in rename() -> correctly non-final -> SATISFIED

    public ConstancyBad(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }
}
