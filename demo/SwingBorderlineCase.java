// DEMO FILE 3: Swing UI class — borderline case



import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

abstract class BasePanel extends JPanel {
    private String title;

    public BasePanel(String title) {
        this.title = title;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public abstract void initComponents();
}

class BettingPanel extends BasePanel implements ActionListener {
    private JButton placeBetButton;
    private JLabel statusLabel;
    private JTextField amountField;

    public BettingPanel() {
        super("Betting Panel");
        initComponents();
    }

    @Override
    public void initComponents() {
        placeBetButton = new JButton("Place Bet");
        statusLabel = new JLabel("No bet placed");
        amountField = new JTextField(10);

        placeBetButton.addActionListener(this);

        add(new JLabel("Amount:"));
        add(amountField);
        add(placeBetButton);
        add(statusLabel);
    }

    // Overloading
    public void updateStatus(String message) {
        statusLabel.setText(message);
    }

    public void updateStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String amount = amountField.getText();
        updateStatus("Bet placed: €" + amount, Color.GREEN);
    }
}
