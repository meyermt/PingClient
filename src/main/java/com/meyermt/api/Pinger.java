package com.meyermt.api;

import java.io.IOException;
import java.net.*;
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
    private int receiveCounter = 0;
    private List<Long> delays = new ArrayList<>();
    private LocalDateTime overallStart;
    private LocalDateTime overallEnd;

    public Pinger(InetAddress ip, int port, int count, int period, int timeout) {
        this.ip = ip;
        this.port = port;
        this.count = count;
        this.period = period;
        this.timeout = timeout;
        // thread pool same size as total count in case they all timeout and period is much less than timeout
        this.executor = new ScheduledThreadPoolExecutor(count);
    }

    private class PingerTask implements Runnable {
        private int sequenceNumber = 1;
        public void run() {
            try {
                // set up new client socket
                DatagramSocket client = new DatagramSocket();
                client.setSoTimeout(timeout);

                // document starting time
                LocalDateTime sendingTime = LocalDateTime.now();
                if (sequenceNumber == 1) {
                    overallStart = sendingTime;
                }

                // put together a packet
                String payload = "PING " + sequenceNumber + " " + sendingTime + "\r\n";
                byte[] payloadBytes = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(payloadBytes, payloadBytes.length, ip, port);

                // send and try receiving packet
                client.send(packet);
                packet = new DatagramPacket(payloadBytes, payloadBytes.length);
                client.receive(packet);

                LocalDateTime receivingTime = LocalDateTime.now();
                long delay = sendingTime.until(receivingTime, ChronoUnit.MILLIS);
                // print out receive
                System.out.println("PONG " + ip.getHostAddress() + ": seq=" + sequenceNumber + " time=" + delay + " ms");

                // collect for stats
                delays.add(delay);
                receiveCounter++;

                // end executing at the count
                if (++sequenceNumber > count) {
                    overallEnd = receivingTime;
                    future.cancel(false);
                }

            } catch (SocketTimeoutException e) {
                // if timed out, still need to get the time in case this was last transaction
                LocalDateTime receivingTime = LocalDateTime.now();
                if (++sequenceNumber > count) {
                    overallEnd = receivingTime;
                    future.cancel(false);
                }
            } catch (IOException e) {

            }
        }
    }

    public void pingAndRetrieve() {
        System.out.println("PING " + ip.getHostAddress());
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        futures.add(executor.scheduleAtFixedRate(new PingerTask(), 0, period, TimeUnit.MILLISECONDS));
        executor.shutdown();
        while (!executor.isTerminated()) {
            // sit and spin while we process
        }
    }

    public long getOverallDiffInMillis() {
        return overallStart.until(overallEnd, ChronoUnit.MILLIS);
    }

    public List<Long> getDelays() {
        return delays;
    }

    public int getSuccessCount() {
        return receiveCounter;
    }
}
