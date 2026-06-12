// DEMO FILE 1: Clean submission — should PASS
import java.util.List;
import java.util.ArrayList;

interface Playable {
    void play();
    double getOdds();
}

abstract class BettingEntity {
    private String id;
    private double balance;

    public BettingEntity(String id, double balance) {
        this.id = id;
        this.balance = balance;
    }

    public String getId() { return id; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public abstract String describe();
}

class Bet extends BettingEntity implements Playable {
    private String event;
    private double odds;

    public Bet(String id, String event, double odds, double stake) {
        super(id, stake);
        this.event = event;
        this.odds = odds;
    }

    public String getEvent() { return event; }
    public double getOdds() { return odds; }

    // Overloading
    public void play() {
        System.out.println("Playing bet on: " + event);
    }

    public void play(String message) {
        System.out.println(message + ": " + event);
    }

    @Override
    public String describe() {
        return "Bet[" + getId() + "] on " + event + " @ " + odds;
    }
}

class GenericRepository<T> {
    private List<T> items = new ArrayList<>();

    public void add(T item) { items.add(item); }
    public T get(int index) { return items.get(index); }
    public int size() { return items.size(); }
}
