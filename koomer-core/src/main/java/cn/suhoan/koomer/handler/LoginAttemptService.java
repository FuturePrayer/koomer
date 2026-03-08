package cn.suhoan.koomer.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 登录尝试服务，用于跟踪认证失败次数并封禁IP。
 * <p>
 * 在指定时间窗口内，如果某个IP的认证失败次数超过阈值，
 * 则将该IP封禁一段时间。
 *
 * @author wangzefeng
 */
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final int maxAttempts;
    private final long windowMillis;
    private final long banDurationMillis;

    // IP → 失败记录
    private final ConcurrentMap<String, AttemptRecord> attemptMap = new ConcurrentHashMap<>();
    // IP → 封禁截止时间戳
    private final ConcurrentMap<String, Long> banMap = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts      时间窗口内允许的最大失败次数
     * @param windowSeconds    时间窗口（秒）
     * @param banDurationSeconds 封禁时长（秒）
     */
    public LoginAttemptService(int maxAttempts, int windowSeconds, int banDurationSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
        this.banDurationMillis = banDurationSeconds * 1000L;
    }

    /**
     * 检查指定IP是否处于封禁状态。
     *
     * @param ip 客户端IP地址
     * @return true表示被封禁
     */
    public boolean isBanned(String ip) {
        Long banUntil = banMap.get(ip);
        if (banUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() >= banUntil) {
            // 封禁已过期，移除记录
            banMap.remove(ip);
            attemptMap.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * 记录一次认证失败。如果失败次数超过阈值则封禁该IP。
     *
     * @param ip 客户端IP地址
     */
    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();

        AttemptRecord record = attemptMap.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart > windowMillis) {
                // 第一次失败 或 时间窗口已过期，重新开始计数
                return new AttemptRecord(now, 1);
            }
            // 在窗口内，递增计数
            existing.count++;
            return existing;
        });

        if (record.count >= maxAttempts) {
            // 达到阈值，封禁
            long banUntil = now + banDurationMillis;
            banMap.put(ip, banUntil);
            attemptMap.remove(ip);
            log.warn("IP {} has been banned for {} seconds due to {} failed login attempts within {} seconds.",
                    ip, banDurationMillis / 1000, maxAttempts, windowMillis / 1000);
        }
    }

    /**
     * 认证成功时清除该IP的失败记录。
     *
     * @param ip 客户端IP地址
     */
    public void recordSuccess(String ip) {
        attemptMap.remove(ip);
    }

    /**
     * 获取指定IP的剩余封禁秒数。
     *
     * @param ip 客户端IP地址
     * @return 剩余封禁秒数，如果未被封禁则返回0
     */
    public long getRemainingBanSeconds(String ip) {
        Long banUntil = banMap.get(ip);
        if (banUntil == null) {
            return 0;
        }
        long remaining = banUntil - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    private static class AttemptRecord {
        long windowStart;
        int count;

        AttemptRecord(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

