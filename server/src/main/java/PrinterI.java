import Demo.Response;
import com.zeroc.Ice.Current;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PrinterI implements Demo.Printer {

    @Override
    public Response printString(String s, Current current) {
        long t0 = System.nanoTime();
        Response resp = new Response();


        Parsed m = parsePrefixed(s);
        String user = (m != null ? m.user : "");
        String host = (m != null ? m.host : "");
        String body = (m != null ? m.payload : s);
        String tag = (!user.isEmpty() && !host.isEmpty()) ? ("[" + user + "@" + host + "] ") : "";

        try {
            String payload = body.trim();

            // exit -> responder bye (el cliente corta su bucle)
            if (payload.equalsIgnoreCase("exit")) {
                System.out.println(tag + "exit");
                resp.value = "bye";
                return done(resp, t0);
            }

            // 2a) entero positivo -> imprimir fibonacci(n), retornar factores primos
            if (isPositiveInteger(payload)) {
                long n = Long.parseLong(payload);
                List<Long> fib = fibonacci(n);
                System.out.println(tag + "fibonacci(" + n + "): " + join(fib, ", "));
                List<Long> fac = primeFactors(n);
                resp.value = n + " = " + join(fac, "*");
                return done(resp, t0);
            }

            // 2b) listifs -> imprimir y retornar interfaces del SERVIDOR
            if (payload.equalsIgnoreCase("listifs")) {
                List<String> ifs = listInterfaces();
                System.out.println(tag + "listifs ->\n" + String.join("\n", ifs));
                resp.value = String.join(" | ", ifs);
                return done(resp, t0);
            }

            // 2c) listports <IPv4> -> imprimir y retornar puertos/servicios abiertos
            if (startsWithIgnoreCase(payload, "listports")) {
                String[] parts = payload.split("\\s+");
                if (parts.length < 2) {
                    resp.value = "ERR Usage: listports <IPv4>";
                    return done(resp, t0);
                }
                String ip = parts[1];
                List<Integer> open = scanTcp(ip, 1, 1024, 120);
                String res = open.isEmpty()
                        ? "No open TCP ports in 1-1024"
                        : open.stream().map(p -> p + "/" + serviceName(p)).collect(Collectors.joining(" | "));
                System.out.println(tag + "listports " + ip + " -> " + res);
                resp.value = res;
                return done(resp, t0);
            }


            if (payload.startsWith("!")) {
                String cmd = payload.substring(1).trim();
                String out = runCommandSafe(cmd, Duration.ofSeconds(5));
                System.out.println(tag + "!" + cmd + " ->\n" + out);
                resp.value = out.replaceAll("\\s+", " ").trim();
                return done(resp, t0);
            }


            System.out.println(tag + payload);
            resp.value = "OK " + payload;
            return done(resp, t0);

        } catch (Exception e) {
            resp.value = "ERR " + e.getMessage();
            return done(resp, t0);
        }
    }

    // ===== Helpers =====
    private static class Parsed { final String user, host, payload; Parsed(String u,String h,String p){user=u;host=h;payload=p;} }
    private Parsed parsePrefixed(String s){
        int a = s.indexOf(':'), b = (a<0)? -1 : s.indexOf(':', a+1);
        if (a<0 || b<0) return null;
        String u = s.substring(0,a).trim();
        String h = s.substring(a+1,b).trim();
        String p = s.substring(b+1);
        if (u.isEmpty() || h.isEmpty()) return null;
        return new Parsed(u,h,p);
    }

    private boolean isPositiveInteger(String s){
        try { return Long.parseLong(s.trim()) > 0; } catch (Exception e){ return false; }
    }

    private List<Long> fibonacci(long n){
        List<Long> r = new ArrayList<>();
        long a=0, b=1;
        for (int i=0;i<n;i++){ r.add(a); long c=a+b; a=b; b=c; }
        return r;
    }

    private List<Long> primeFactors(long n){
        List<Long> f = new ArrayList<>();
        long x = n;
        for (long p=2; p*p<=x; p+=(p==2?1:2)) {
            while (x % p == 0) { f.add(p); x/=p; }
        }
        if (x>1) f.add(x);
        return f;
    }

    private List<String> listInterfaces(){
        List<String> r = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(en)) {
                if (!ni.isUp()) continue;
                String addrs = Collections.list(ni.getInetAddresses()).stream()
                        .map(InetAddress::getHostAddress).collect(Collectors.joining(","));
                r.add(ni.getName() + " -> " + addrs);
            }
        } catch (Exception ignored) {}
        return r;
    }

    private List<Integer> scanTcp(String ip, int start, int end, int timeoutMs){
        List<Integer> open = new ArrayList<>();
        for (int p=start; p<=end; p++){
            try (Socket s = new Socket()){
                s.connect(new InetSocketAddress(ip, p), timeoutMs);
                open.add(p);
            } catch (Exception ignored) {}
        }
        int[] extra = {8080, 3306, 5432, 6379};
        for (int p: extra){
            if (p < start || p > end){
                try (Socket s = new Socket()){
                    s.connect(new InetSocketAddress(ip, p), timeoutMs);
                    open.add(p);
                } catch (Exception ignored) {}
            }
        }
        return open;
    }

    private String serviceName(int port){
        Map<Integer,String> m = new HashMap<>();
        m.put(20,"ftp-data"); m.put(21,"ftp"); m.put(22,"ssh"); m.put(23,"telnet"); m.put(25,"smtp"); m.put(53,"dns");
        m.put(67,"dhcp"); m.put(68,"dhcp"); m.put(80,"http"); m.put(110,"pop3"); m.put(123,"ntp"); m.put(143,"imap");
        m.put(443,"https"); m.put(587,"smtp-sub"); m.put(993,"imaps"); m.put(995,"pop3s");
        m.put(3306,"mysql"); m.put(5432,"postgres"); m.put(6379,"redis"); m.put(8080,"http-alt");
        return m.getOrDefault(port, "tcp");
    }

    private String runCommandSafe(String cmd, Duration timeout){
        if (cmd.length() > 200) return "ERR command too long";
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            List<String> command = os.contains("win")
                    ? Arrays.asList("cmd.exe", "/c", cmd)
                    : Arrays.asList("bash", "-lc", cmd);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!ok) {
                p.destroyForcibly();
                return "ERR timeout";
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String out = r.lines().collect(Collectors.joining("\n"));
                return out.isEmpty() ? "(no output)" : out;
            }
        } catch (Exception e) {
            return "ERR " + e.getMessage();
        }
    }

    private boolean startsWithIgnoreCase(String s, String prefix){
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String join(List<?> xs, String sep){
        return xs.stream().map(Object::toString).collect(Collectors.joining(sep));
    }

    private Response done(Response r, long t0){
        r.responseTime = (int)((System.nanoTime() - t0) / 1_000_000);
        return r;
    }
}
