package com.meyermt.api;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Schedules and executes the multi-threaded pinging client. Scheduler thread spins up thread per ping, and each ping
 * spins up a thread to avoid blocking on waiting for each packet receive.
 * Created by michaelmeyer on 2/26/17.
 */
public class Pinger {

    private final ScheduledThreadPoolExecutor executor;
    static ScheduledFuture<?> future;
    private final InetAddress ip;
    private final int port;
    private final int count;
    private final int period;
    private final int timeout;
    private int receiveCounter = 0;
    private List<Long> delays = new ArrayList<>();
    private LocalDateTime overallStart;
    private LocalDateTime overallEnd;
    private int sequenceNumber = 0;
    private List<Thread> threads = new ArrayList<>();

    /**
     * Instantiates a new Pinger. Requires server and scheduling information.
     *
     * @param ip      the ip address of the ping server
     * @param port    the port of the ping server
     * @param count   the count of pings to send
     * @param period  the period in milliseconds between pings
     * @param timeout the timeout to wait for before stopping a ping packet receive
     */
    public Pinger(InetAddress ip, int port, int count, int period, int timeout) {
        this.ip = ip;
        this.port = port;
        this.count = count;
        this.period = period;
        this.timeout = timeout;
        // thread pool same size as total count in case they all timeout and period is much less than timeout
        this.executor = new ScheduledThreadPoolExecutor(count);
        for (int i = 0; i < count; i++) {
            threads.add(new InnerThread(i + 1));
        }
    }

    /**
     * Thread that handles the work of sending and receiving the packet. Exists to allow concurrency while blocking on
     * the receive operation
     */
    private class InnerThread extends Thread {

        private int seq;

        /**
         * Instantiates a new Inner thread.
         *
         * @param innerSeq sequence number for the given thread
         */
        public InnerThread(int innerSeq) {
            this.seq = innerSeq;
        }

        public void run() {
            LocalDateTime sendingTime = LocalDateTime.now();
            try {
                // set up new client socket
                DatagramSocket client = new DatagramSocket();
                client.setSoTimeout(timeout);

                // document starting time
                if (seq == 1) {
                    overallStart = sendingTime;
                }

                // put together a packet
                String payload = "PING " + seq + " " + sendingTime + "\r\n";
                byte[] payloadBytes = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(payloadBytes, payloadBytes.length, ip, port);

                // send and try receiving packet
                client.send(packet);
                packet = new DatagramPacket(payloadBytes, payloadBytes.length);
                client.receive(packet);

                LocalDateTime receivingTime = LocalDateTime.now();
                long delay = sendingTime.until(receivingTime, ChronoUnit.MILLIS);
                // print out receive
                System.out.println("PONG " + ip.getHostAddress() + ": seq=" + seq + " time=" + delay + " ms");

                // collect state
                overallEnd = receivingTime;
                delays.add(delay);
                receiveCounter++;
                overallEnd = receivingTime;

            } catch (SocketTimeoutException e) {
                // if timed out, still need to get the time in case this was last transaction
                LocalDateTime receivingTime = LocalDateTime.now();
                overallEnd = receivingTime;
            } catch (IOException e) {

            }
        }
    }

    /**
     * TimerTask extension that gets run by the scheduling thread executor
     */
    private class PingerTask extends TimerTask {

        private List<Thread> thr;

        /**
         * Instantiates a new Pinger task.
         *
         * @param thr the inner thread to run
         */
        public PingerTask(List<Thread> thr) {
            this.thr = thr;
        }

        public void run() {
            thr.get(sequenceNumber).start();
            if (++sequenceNumber > count) {
                future.cancel(false);
            }

        }
    }

    /**
     * Pings the server and stores pertinent stats in class state
     */
    public void pingAndRetrieve() {
        System.out.println("PING " + ip.getHostAddress());
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
        executor.scheduleAtFixedRate(new PingerTask(threads), 0, period, TimeUnit.MILLISECONDS);
        blockOnSchedule();
        blockOnThreads();
    }

    /**
     * Gets overall diff in milliseconds
     *
     * @return the overall diff in milliseconds
     */
    public long getOverallDiffInMillis() {
        return overallStart.until(overallEnd, ChronoUnit.MILLIS);
    }

    /**
     * Gets list of delays in milliseconds corresponding to each ping. Timeouts not counted.
     *
     * @return the total delays/timeouts
     */
    public List<Long> getDelays() {
        return delays;
    }

    /**
     * Gets the total number of successful pongs back from the server
     *
     * @return the success count
     */
    public int getSuccessCount() {
        return receiveCounter;
    }

    /*
        Blocks main thread execution until schedule executor completes
     */
    private void blockOnSchedule() {
        while (!executor.isTerminated()) {
            executor.shutdown();
        }
    }

    /*
        Blocks the main thread until all threads have completed
     */
    private void blockOnThreads() {
        // block and wait for threads to complete
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unable to wait on thread", e);
            }
        });
    }
}
