package com.meyermt.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by michaelmeyer on 2/26/17.
 */
public class Main {

    private static final String IP_ARG = "--server_ip", PORT_ARG = "--server_port", COUNT_ARG = "--count", PERIOD_ARG = "--period",
                            TIMEOUT_ARG = "--timeout";

    public static void main(String[] args) {
        // Should have exactly 5 space delimited args (10 with flags) exactly correct order
        String ip = "";
        int port = 0;
        int count = 0;
        int period = 0;
        int timeout = 0;
        if (args.length == 10 && args[0].equals(IP_ARG) && args[2].equals(PORT_ARG) && args[4].equals(COUNT_ARG) &&
                args[6].equals(PERIOD_ARG) && args[8].equals(TIMEOUT_ARG)) {
            ip = args[1];
            port = Integer.parseInt(args[3]);
            count = Integer.parseInt(args[5]);
            period = Integer.parseInt(args[7]);
            timeout = Integer.parseInt(args[9]);
        } else {
            System.out.println("Illegal arguments. Should be run with arguments: --server_ip <ip> --server_port <desired port number> --count <count> --period <period interval> --timeout <timeout>");
            System.exit(1);
        }
        runClient(ip, port, count, period, timeout);
    }

    /*
        Starts the web server. Continuous loop that listens for clients. For each client connection, a new thread is run.
     */
    private static void runClient(String ip, int port, int count, int period, int timeout) {
        try {
            InetAddress serverIp = InetAddress.getByName(ip);
            Pinger pinger = new Pinger(serverIp, port, count, period, timeout);
            pinger.pingAndRetrieve();
            writeOutput(ip, count, pinger);
        } catch(UnknownHostException e) {
            e.printStackTrace();
            System.out.println("Exception opening socket");
            System.exit(1);
        }
    }

    private static void writeOutput(String ip, int count, Pinger pinger) {
        System.out.println("--- " + ip + " ping statistics ---");
        int received = pinger.getSuccessCount();
        int loss = ((count - received) / count) * 100;
        long totalTime = pinger.getOverallDiffInMillis();
        System.out.println(count + " transmitted, " + received + " received, " + loss + "% loss, time " + totalTime + "ms");
        long fastest = pinger.getDelays().stream()
                .mapToLong(Long::longValue).min().orElseThrow(() -> new RuntimeException("Impossibly, no long found. Failing program"));
        long slowest = pinger.getDelays().stream()
                .mapToLong(Long::longValue).max().orElseThrow(() -> new RuntimeException("Impossibly, no long found. Failing program"));
        Double avg = pinger.getDelays().stream()
                .mapToLong(Long::longValue).average().orElseThrow(() -> new RuntimeException("Impossibly, no long found. Failing program"));
        // easier to read ints
        int intAvg = avg.intValue();
        System.out.println("rtt min/avg/max = " + fastest + "/" + intAvg + "/" + slowest);
    }
}
