/**
 * Constancy correct: 'id' is final (never changes); 'hits' is genuinely mutated (hits++), so being
 * non-final is the right choice. All fields private, so visibility is correct too.
 */
public class ConstancyGood {
    private final int id;
    private int hits;

    public ConstancyGood(int id) {
        this.id = id;
        this.hits = 0;
    }

    public void hit() {
        this.hits++;
    }

    public int getId() {
        return id;
    }

    public int getHits() {
        return hits;
    }
}
