package HockeyLive.Server;

import HockeyLive.Common.Communication.ClientMessage;
import HockeyLive.Common.Communication.ServerMessage;
import HockeyLive.Common.Constants;
import HockeyLive.Common.Models.Bet;
import HockeyLive.Common.Models.Game;
import HockeyLive.Common.Models.GameInfo;
import HockeyLive.Server.Communication.ServerSocket;
import HockeyLive.Server.Factory.GameFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Created by Micha�l on 10/12/2015.
 */
public class Server implements Runnable {
    private List<Game> runningGames;
    private ConcurrentMap<Integer, GameInfo> runningGameInfos;
    private ConcurrentMap<Integer, List<Bet>> placedBets;
    private ConcurrentMap<Integer, ConcurrentMap<InetAddress, ConcurrentMap<Integer, ClientMessage>>> acks;
    private ServerSocket socket;
    private Thread serverThread;

    private Lock gameUpdateLock;

    public Server() {
        runningGames = new ArrayList<>();
        runningGameInfos = new ConcurrentHashMap<>();
        placedBets = new ConcurrentHashMap<>();
        gameUpdateLock = new ReentrantLock();
        acks = new ConcurrentHashMap<>();
        InitializeGames();
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    public void execute() {
        try {
            socket = new ServerSocket(Constants.SERVER_COMM_PORT);
            ExecutorService threadPool = Executors.newCachedThreadPool();

            while (true) {

                try {
                    ClientMessage clientMessage = socket.GetMessage();

                    Runnable handler = new HandlerThread(this, clientMessage);
                    threadPool.submit(handler);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void SendReply(ClientMessage clientMessage, Object data) {
        ServerMessage serverMessage = new ServerMessage(clientMessage.GetIPAddress(),
                clientMessage.GetPort(),
                clientMessage.getReceiverIp(),
                clientMessage.getReceiverPort(),
                clientMessage.getID(),
                data);

        socket.Send(serverMessage);
    }

    public synchronized void AddGame(Game game, GameInfo info) {
        if (runningGames.size() < 10) {
            runningGames.add(game);
            runningGameInfos.put(game.getGameID(), info);
            placedBets.put(game.getGameID(), new ArrayList<>());
            acks.put(game.getGameID(), new ConcurrentHashMap<>());
        }
    }

    public synchronized List<Game> GetGames() {
        return runningGames;
    }

    public synchronized List<Game> GetNonCompletedGames() {
        return runningGames.stream().filter(g -> !g.isCompleted()).collect(Collectors.toList());
    }

    public synchronized GameInfo GetGameInfo(Integer gameID) {
        return runningGameInfos.get(gameID);
    }

    public synchronized GameInfo GetGameInfo(Object gameID) {
        try {
            Integer g = (Integer)gameID;
            return GetGameInfo(g);
        } catch (Exception e) {
            return null;
        }
    }

    public void AddAck(ClientMessage message) {
        Bet bet = (Bet) message.getData();

        ConcurrentMap<InetAddress, ConcurrentMap<Integer, ClientMessage>> gameAcks = acks.get(message.getID());
        gameAcks.putIfAbsent(message.GetIPAddress(), new ConcurrentHashMap<>());
        ConcurrentMap<Integer, ClientMessage> addressAcks = gameAcks.get(message.GetIPAddress());
        addressAcks.put(message.GetPort(), message);

        acks.notifyAll();
    }

    public synchronized void PlaceBet(Bet bet, ClientMessage message) {
        Game game = runningGames.get(bet.getGameID());
        GameInfo info = runningGameInfos.get(game.getGameID());

        boolean added = false;

        InetAddress localhost;
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        if (info.getPeriod() <= 2) {
            added = true;
            (placedBets.get(bet.getGameID())).add(bet);
        }

        socket.Send(new ServerMessage(localhost, Constants.SERVER_COMM_PORT,
                message.GetIPAddress(), message.GetPort(), message.getID(), added));

        while (info.getPeriod() <= 3) {
            try {
                game.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        bet.setAmountGained(ComputeAmountGained(bet, game));

        ServerMessage serverMessage = new ServerMessage(message.GetIPAddress(),
                message.GetPort(),
                message.getReceiverIp(),
                message.getReceiverPort(),
                message.getID(),
                bet);

        while (!(acks.containsKey(game.getGameID())
                && acks.get(game.getGameID()).containsKey(message.GetIPAddress())
                && acks.get(game.getGameID()).get(message.GetIPAddress()).containsKey(message.GetPort()))) {
            socket.Send(serverMessage);
            try {
                acks.wait(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void PlaceBet(Object bet, ClientMessage message) {
        try {
            Bet b = (Bet) bet;
            PlaceBet(b, message);
        } catch (Exception e) {
            SendReply(message, false);
        }
    }

    private double ComputeAmountGained(Bet bet, Game game) {
        List<Bet> bets = placedBets.get(game.getGameID());
        GameInfo info = GetGameInfo(game);

        double totalAmountHost = 0;
        double totalAmountVisitor = 0;

        for (int i = 0; i < bets.size(); i++) {
            Bet b = bets.get(i);

            if (b.getBetOn().equals(game.getHost())) {
                totalAmountHost += b.getAmount();
            } else {
                totalAmountVisitor += b.getAmount();
            }
        }

        double amountGained = 0;

        if (info.getHostGoalsTotal() > info.getVisitorGoalsTotal()) {
            if (bet.getBetOn().equals(game.getHost())) {
                amountGained = 0.75 * (totalAmountHost + totalAmountVisitor)
                        * (bet.getAmount() / totalAmountHost);
            } else {
                amountGained = 0 - bet.getAmount();
            }
        } else if (info.getHostGoalsTotal() < info.getVisitorGoalsTotal()) {
            if (bet.getBetOn().equals(game.getHost())) {
                amountGained = 0.75 * (totalAmountHost + totalAmountVisitor)
                        * (bet.getAmount() / totalAmountVisitor);
            } else {
                amountGained = 0 - bet.getAmount();
            }
        }

        return amountGained;
    }

    @Override
    public void run() {
        execute();
    }

    public void start() {
        serverThread = new Thread(this);
        serverThread.start();
    }

    private void InitializeGames() {
        for (int i = 0; i < 10; ++i) {
            Game g = GameFactory.GenerateGame();
            GameInfo info = GameFactory.GenerateGameInfo(g);
            AddGame(g, info);
        }
    }

    public void stop() {
        serverThread.interrupt();
        socket.CloseSocket();
    }

    public void LockForUpdate() {
        gameUpdateLock.lock();
    }

    public void UnlockUpdates() {
        gameUpdateLock.unlock();
    }

    public void SetPeriodLength(int minutes) {
        GameFactory.UpdatePeriodLength(minutes);
    }
}
