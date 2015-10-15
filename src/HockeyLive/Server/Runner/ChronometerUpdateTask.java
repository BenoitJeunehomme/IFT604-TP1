package HockeyLive.Server.Runner;

import HockeyLive.Common.Models.Game;
import HockeyLive.Common.Models.GameInfo;
import HockeyLive.Common.Models.Penalty;
import HockeyLive.Common.Models.Side;
import HockeyLive.Server.Server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

/**
 * Created by Micha�l on 10/14/2015.
 */
public class ChronometerUpdateTask extends TimerTask {
    private final Server server;
    private final int TICK_VALUE = 30;

    public ChronometerUpdateTask(Server server) {
        this.server = server;
    }

    @Override
    public void run() {
        server.LockForUpdate();
        for (Game g : server.GetNonCompletedGames()) {
            GameInfo info = server.GetGameInfo(g);
            info.decPeriodChronometer(Duration.ofSeconds(TICK_VALUE));

            //Verify if we have completed a period
            Duration currentChronometer = info.getPeriodChronometer();
            if (currentChronometer.isZero() || currentChronometer.isNegative()) {
                // If chronometer is 0 and period is currently 3
                if (info.getPeriod() == 3) {
                    g.setCompleted(true);
                    info.setPeriodChronometer(Duration.ofMinutes(0));
                } else
                    info.incPeriod();
            }

            // If completed, clear all penalties, update and remove completed penalties otherwise
            if (g.isCompleted()) ClearPenalties(info);
            else UpdateAllPenalties(info);
        }
        server.UnlockUpdates();
    }

    private void ClearPenalties(GameInfo info) {
        info.getVisitorPenalties().clear();
        info.getHostPenalties().clear();
    }

    private void UpdateAllPenalties(GameInfo info) {
        UpdatePenalties(info, Side.Host);
        UpdatePenalties(info, Side.Visitor);
    }

    private void UpdatePenalties(GameInfo info, Side side) {
        List<Penalty> toRemove = new ArrayList<>();
        for (Penalty p : info.getSidePenalties(side)) {
            p.decTimeLeft(Duration.ofSeconds(TICK_VALUE));
            Duration timeLeft = p.getTimeLeft();
            if (timeLeft.isZero() || timeLeft.isNegative())
                toRemove.add(p);
        }

        for (Penalty p : toRemove)
            info.removeSidePenalty(p, side);
    }

}