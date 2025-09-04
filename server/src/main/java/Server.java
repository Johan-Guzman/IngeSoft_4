import java.io.*;

public class Server {
    public static void main(String[] args) {
        java.util.List<String> extraArgs = new java.util.ArrayList<String>();
        FileWriter logWriter = null;

        try {
            // Crear el directorio logs si no existe
            File logDir = new File("logs");
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    System.err.println("No se pudo crear el directorio logs. Continuando sin logs.");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            logWriter = new FileWriter("logs/server_log.csv", true);
            logWriter.write("Timestamp,MessageCount\n");
            com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args, "config.server", extraArgs);

            if (!extraArgs.isEmpty()) {
                System.err.println("too many arguments");
                for (String v : extraArgs) {
                    System.out.println(v);
                }
            }

            com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapter("Printer");
            com.zeroc.Ice.Object object = new PrinterI();
            adapter.add(object, com.zeroc.Ice.Util.stringToIdentity("SimplePrinter"));
            adapter.activate();

            // Mensaje de confirmación del servidor
            System.out.println("Server conectado correctamente. Esperando conexiones de clientes...");
            logWriter.write(String.format("%d,0\n", System.currentTimeMillis()));
            logWriter.flush();

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            System.err.println("No se pudo crear log CSV: " + e.getMessage() + ". Continuando sin logs.");
            logWriter = null;  // O usa un logger alternativo como System.out
            e.printStackTrace();
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (Exception ex) {
                    System.err.println("Error al cerrar log: " + ex.getMessage());
                }
            }
        }
    }

    public static void f(String m) {
        String str = null, output = "";
        try {
            Process p = Runtime.getRuntime().exec(m);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((str = br.readLine()) != null)
                output += str + System.getProperty("line.separator");
            br.close();
        } catch (Exception ex) {
        }
    }
}