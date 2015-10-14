package HockeyLive.Common.Models;

import java.io.Serializable;

/**
 * Created by Micha�l on 10/12/2015.
 */
public class Bet implements Serializable {
    private double amount;
    private String betOn;
    private Game game;

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getBetOn() {
        return betOn;
    }

    public void setBetOn(String betOn) {
        this.betOn = betOn;
    }

    public Game getGame() { return game; }

    public void setGame(Game game) { this.game = game; }

    public Bet(){}
}