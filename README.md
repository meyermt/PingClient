# PingClient

Client that will send pings to a ping server that pings it back (over UDP, not ICMP).

## How to Run the Ping Client

Must have Java 8 installed in order to run this client. This also assumes that you have a PingServer running and know the
ip and port.

1. From the project's root dir, run `javac -d bin src/main/java/com/meyermt/api/*.java`
2. From the project's root dir, run `java -cp bin com.meyermt.api.Main --server_ip <ip> --server_port <desired port number> --count <count> --period <period interval> --timeout <timeout>`

Note that args are delimited only by a space and not an "=" symbol.

## Known Issues

1. While not an issue, the current model does more work with threads than it probably needs to. Inner thread should probably
be managed by an ExecutorService that can be blocked in an easier fashion.