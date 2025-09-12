package cn.suhoan.koomer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        int port = 10808;
        String host = "::0";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") || args[i].equals("--port")) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    log.error("Invalid port number: " + args[i + 1]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.error("Missing port number");
                }
            }
            if (args[i].equals("-h") || args[i].equals("--host")) {
                try {
                    host = args[i + 1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.error("Missing host");
                }
                break;
            }
        }
        new Socks5ProxyServer(host, port).start();
    }
}