package cn.suhoan.koomer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    static void main(String[] args) {
        try {
            int port = 10808;
            String host = "::0";
            boolean enableAuth = false;
            String username = null;
            String password = null;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-h") || args[i].equals("--help")){
                    IO.println("""
                            Usage: java -jar koomer.jar [options]
                            Options:
                              -h, --help                  Show this help message and exit.
                              -p, --port <port>           Set the port number to listen on.
                              -l, --host <host>           Set the host to listen on.
                              -a, --enable-auth           Enable authentication.
                              -u, --username <username>     Set the username for authentication.
                              -w, --password <password>     Set the password for authentication.
                            """);
                    return;
                }
                if (args[i].equals("-p") || args[i].equals("--port")) {
                    try {
                        port = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid port number: {}, program will start on default port {}.", args[i + 1], port);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        log.warn("Missing port number, program will start on default port {}.", port);
                    }
                }
                if (args[i].equals("-l") || args[i].equals("--host")) {
                    try {
                        host = args[i + 1];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        log.warn("Missing host, program will start on default host {}.", host);
                    }
                }
                if (args[i].equals("-a") || args[i].equals("--enable-auth")) {
                    enableAuth = true;
                }
                if (args[i].equals("-u") || args[i].equals("--username")) {
                    try {
                        username = args[i + 1];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new IllegalArgumentException("Missing username");
                    }
                }
                if (args[i].equals("-w") || args[i].equals("--password")) {
                    try {
                        password = args[i + 1];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new IllegalArgumentException("Missing password");
                    }
                }
            }
            checkParam:
            if (enableAuth) {
                if (username == null || username.isBlank()) {
                    log.warn("The username is null or empty, authentication mode will be disabled.");
                    enableAuth = false;
                    username = null;
                    password = null;
                    break checkParam;
                }
                if (password == null || password.isBlank()) {
                    log.warn("The password is null or empty, authentication mode will be disabled.");
                    enableAuth = false;
                    username = null;
                    password = null;
                }
            } else {
                if ((username != null && !username.isBlank()) || (password != null && !password.isBlank())){
                    log.warn("The authentication mode is not enabled, the username and password parameters will be ignored.");
                }
                username = null;
                password = null;
            }
            new Socks5ProxyServer(host, port, enableAuth, username, password).start();
        } catch (Exception e) {
            log.error("Error starting proxy server", e);
            System.exit(1);
        }
    }
}