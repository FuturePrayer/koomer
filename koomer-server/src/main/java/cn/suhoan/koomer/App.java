package cn.suhoan.koomer;

import cn.suhoan.koomer.handler.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
@Command(name = "koomer", mixinStandardHelpOptions = true, version = "koomer 1.1",
        description = "A high-performance SOCKS5 proxy server based on Netty.")
public class App implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Option(names = {"-l", "--host"}, description = "Set the host to listen on (default: ${DEFAULT-VALUE}).", defaultValue = "::0")
    private String host;

    @Option(names = {"-p", "--port"}, description = "Set the port number to listen on (default: ${DEFAULT-VALUE}).", defaultValue = "10808")
    private int port;

    @Option(names = {"-a", "--enable-auth"}, description = "Enable authentication (default: ${DEFAULT-VALUE}).", defaultValue = "false")
    private boolean enableAuth;

    @Option(names = {"-u", "--username"}, description = "Set the username for authentication.")
    private String username;

    @Option(names = {"-w", "--password"}, description = "Set the password for authentication.")
    private String password;

    @Option(names = {"--max-attempts"}, description = "Max failed login attempts before banning (default: ${DEFAULT-VALUE}). Only effective when auth is enabled.", defaultValue = "5")
    private int maxAttempts;

    @Option(names = {"--auth-window"}, description = "Time window in seconds for counting failed attempts (default: ${DEFAULT-VALUE}). Only effective when auth is enabled.", defaultValue = "300")
    private int authWindow;

    @Option(names = {"--ban-duration"}, description = "Ban duration in seconds after exceeding max attempts (default: ${DEFAULT-VALUE}). Only effective when auth is enabled.", defaultValue = "3600")
    private int banDuration;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Override
    public void run() {
        try {
            // 参数校验
            if (enableAuth) {
                if (username == null || username.isBlank()) {
                    log.warn("The username is null or empty, authentication mode will be disabled.");
                    enableAuth = false;
                    username = null;
                    password = null;
                } else if (password == null || password.isBlank()) {
                    log.warn("The password is null or empty, authentication mode will be disabled.");
                    enableAuth = false;
                    username = null;
                    password = null;
                }
            } else {
                if ((username != null && !username.isBlank()) || (password != null && !password.isBlank())) {
                    log.warn("The authentication mode is not enabled, the username and password parameters will be ignored.");
                }
                username = null;
                password = null;
            }

            // 创建LoginAttemptService（仅在开启鉴权时生效）
            LoginAttemptService loginAttemptService = null;
            if (enableAuth) {
                loginAttemptService = new LoginAttemptService(maxAttempts, authWindow, banDuration);
                log.info("Login ban policy enabled: max {} attempts within {} seconds, ban duration {} seconds.",
                        maxAttempts, authWindow, banDuration);
            }

            new Socks5ProxyServer(host, port, enableAuth, username, password, loginAttemptService).start();
        } catch (Exception e) {
            log.error("Error starting proxy server", e);
            System.exit(1);
        }
    }
}