package HockeyLive.Server.Communication;

import HockeyLive.Common.Communication.Reply;
import HockeyLive.Common.Communication.Request;
import HockeyLive.Common.helpers.SerializationHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Micha�l on 10/12/2015.
 */
public class ServerSocket {
    private DatagramSocket epSocket;
    private Thread tReceive;
    private BlockingQueue<Request> requestBuffer = new ArrayBlockingQueue<>(50);

    public ServerSocket(int port) throws IOException {
        epSocket = new DatagramSocket(port);
        tReceive = new Thread(() -> {
            Receive();
        });
        tReceive.start();
    }

    public void Receive() {
        byte[] receiveData = new byte[1024];
        while (true) {
            DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
            try {
                epSocket.receive(packet);
                Request req = (Request) SerializationHelper.deserialize(packet.getData());
                requestBuffer.add(req);
            } catch (Exception e) {
                e.printStackTrace();
                CloseSocket();
            }
            if(tReceive.isInterrupted()) break;
        }
    }

    public void SendReply(Reply reply) {
        try {
            byte[] data = SerializationHelper.serialize(reply);
            DatagramPacket packet = new DatagramPacket(data, data.length, reply.GetIPAddress(), reply.GetPort());
            DatagramSocket replySocket = new DatagramSocket();
            replySocket.send(packet);
            replySocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Request GetRequest() throws InterruptedException {
        return requestBuffer.take();
    }

    public void CloseSocket() {
        if (epSocket.isConnected())
            epSocket.close();
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("Finalizing Server Socket");
        tReceive.interrupt();
        super.finalize();
    }
}
