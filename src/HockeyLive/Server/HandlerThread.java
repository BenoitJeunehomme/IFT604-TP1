package HockeyLive.Server;

import HockeyLive.Common.Communication.Request;
import HockeyLive.Server.Communication.ServerSocket;

/**
 * Created by Benoit on 2015-10-13.
 */
public class HandlerThread implements Runnable {

    private ServerSocket socket;
    private Request request;

    public HandlerThread(ServerSocket socket, Request request){
        this.socket = socket;
        this.request = request;
    }

    @Override
    public void run() {
        //System.out.println(Thread.currentThread().getName()+' Start. Command = '+command);
        processCommand();
        //System.out.println(Thread.currentThread().getName()+' End.');
    }

    private void processCommand() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}