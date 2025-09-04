import Demo.Response;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.FileWriter;

public class Client {
    public static void main(String[] args) throws Exception {
        List<String> extraArgs = new ArrayList<>();
        FileWriter logWriter = null;

        try (com.zeroc.Ice.Communicator communicator =
                     com.zeroc.Ice.Util.initialize(args, "config.client", extraArgs)) {

            Demo.PrinterPrx service = Demo.PrinterPrx
                    .checkedCast(communicator.propertyToProxy("Printer.Proxy"));

            if (service == null) {
                throw new Error("Invalid proxy");
            }

            String user = System.getProperty("user.name", "unknown");
            String host = InetAddress.getLocalHost().getHostName();
            String prefix = user + ":" + host + ":";

            // Modo automático si se pasa "auto" como argumento
            if (args.length > 0 && args[0].equals("auto")) {
                int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                String clientId = args.length > 2 ? args[2] : "client1";
                logWriter = new FileWriter("logs/" + clientId + "_log.csv", true);
                logWriter.write("Timestamp,Command,Value,ServerTime,Rtt\n");
                System.out.println("Modo automático: " + iterations + " iteraciones por comando");

                // Comandos para probar todos los casos
                String[] commands = {"hello", "23", "listifs", "listports 127.0.0.1", "!ls"};
                for (String cmd : commands) {
                    System.out.println("Enviando " + iterations + " veces: " + cmd);
                    for (int i = 0; i < iterations; i++) {
                        String payload = prefix + cmd;
                        long t0 = System.nanoTime();
                        Response response = service.printString(payload);
                        long rttMs = (System.nanoTime() - t0) / 1_000_000;
                        System.out.printf("< %s | serverTime=%dms | rtt=%dms%n", response.value, response.responseTime, rttMs);
                        logWriter.write(String.format("%d,%s,%s,%d,%d\n",
                                System.currentTimeMillis(), cmd, response.value, response.responseTime, rttMs));
                        logWriter.flush();
                        Thread.sleep(100); // Pausa para evitar saturar
                    }
                }
                System.out.println("Prueba automática completada");
            } else {
                // Modo manual (original)
                System.out.println("Cliente listo. Escribe tu mensaje (usa 'exit' para salir).");
                Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
                while (true) {
                    System.out.print("> ");
                    String line = sc.nextLine();
                    String payload = prefix + line;
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
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (Exception e) {
                    System.err.println("Error al cerrar log: " + e.getMessage());
                }
            }
        }
    }
}