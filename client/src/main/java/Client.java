import Demo.Response;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        List<String> extraArgs = new ArrayList<>();

        try (com.zeroc.Ice.Communicator communicator =
                     com.zeroc.Ice.Util.initialize(args, "config.client", extraArgs)) {

            Demo.PrinterPrx service = Demo.PrinterPrx
                    .checkedCast(communicator.propertyToProxy("Printer.Proxy"));

            if (service == null) {
                throw new Error("Invalid proxy");
            }

            String user = System.getProperty("user.name", "unknown");
            String host = InetAddress.getLocalHost().getHostName();

            System.out.println("Cliente listo. Escribe tu mensaje (usa 'exit' para salir).");
            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine(); // lo que digita el usuario

                String payload = user + ":" + host + ":" + line;

                long t0 = System.nanoTime();
                Response response = service.printString(payload);
                long rttMs = (System.nanoTime() - t0) / 1_000_000;

                System.out.println("< " + response.value +
                        " | serverTime=" + response.responseTime + "ms" +
                        " | rtt=" + rttMs + "ms");

                if ("exit".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }
        }
    }
}
