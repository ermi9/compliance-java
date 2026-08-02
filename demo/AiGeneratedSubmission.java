// FILE 2: AI-generated style — should FAIL
// Typical LLM output: Records, Streams, Lambdas, public fields

import java.util.List;
import java.util.Arrays;

// LLMs love Records — banned in this course
record MatchOdds(String match, double homeOdds, double awayOdds) {}

public class AiGeneratedSubmission {

    // Public fields — no encapsulation
    public String playerName;
    public double balance;
    public List<String> bets;

    public AiGeneratedSubmission(String playerName, double balance) {
        this.playerName = playerName;
        this.balance = balance;
        this.bets = Arrays.asList("bet1", "bet2", "bet3");
    }

    public void processBets() {
        // Lambda — banned
        bets.forEach(bet -> System.out.println("Processing: " + bet));
    }

    public List<String> getActiveBets() {
        // Stream API — banned
        return bets.stream()
                   .filter(b -> b.startsWith("bet"))
                   .collect(java.util.stream.Collectors.toList());
    }

    public static void main(String[] args) {
        AiGeneratedSubmission s = new AiGeneratedSubmission("Mario", 100.0);
        s.processBets();
    }
}
