package com.meyermt.api;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by michaelmeyer on 2/26/17.
 */
public class Pinger {

    private final ScheduledThreadPoolExecutor executor;
    static ScheduledFuture<?> future;
    private List<Map<String, String>> results = new ArrayList<>();
    private final InetAddress ip;
    private final int port;
    private final int count;
    private final int period;
    private final int timeout;
    private LocalDateTime overallStart;
    private LocalDateTime overallEnd;

    public Pinger(InetAddress ip, int port, int count, int period, int timeout) {
        this.ip = ip;
        this.port = port;
        this.count = count;
        this.period = period;
        this.timeout = timeout;
        // thread pool same size as total count in case they all timeout
        this.executor = new ScheduledThreadPoolExecutor(count);
    }

    private class PingerTask implements Runnable {
        private int sequenceNumber = 0;
        public void run() {
            try {
                DatagramSocket client = new DatagramSocket();
                LocalDateTime sendingTime = LocalDateTime.now();
                if (sequenceNumber == 0) {
                    overallStart = sendingTime;
                }
                String payload = "PING " + sequenceNumber + " " + sendingTime + "\r\n";
                byte[] payloadBytes = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(payloadBytes, payloadBytes.length, ip, port);
                client.send(packet);

                packet = new DatagramPacket(payloadBytes, payloadBytes.length);
                client.receive(packet);
                LocalDateTime receivingTime = LocalDateTime.now();
                String received = new String(packet.getData(), 0, packet.getLength());
                System.out.println("received this: " + received);

                // then print the stuff

                if (++sequenceNumber == count) {
                    overallEnd = receivingTime;
                    future.cancel(false);
                }
            } catch (IOException e) {

            }
        }
    }

    public List<Map<String, String>> pingAndRetrieve() {
        try {
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            future = executor.scheduleAtFixedRate(new PingerTask(), 0, period, TimeUnit.MILLISECONDS);
            // should be more than enough timeout time. Also threads will continue even if shutdown happens
            int shutdownTimeout = (period * count) + (timeout * count) + period;
            executor.awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS);
            executor.shutdown();
        } catch (InterruptedException e) {

        }
        return Collections.emptyList();
    }

    public long getOverallDiffInMillis() {
        return overallStart.until(overallEnd, ChronoUnit.MILLIS);
    }
}
