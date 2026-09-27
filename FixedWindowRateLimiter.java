import java.util.HashMap;
import java.util.Map;

public class FixedWindowRateLimiter {

    private final int maxRequests;
    private final int windowSize;
    private long currentTime;
    private boolean timeFixed;
    private final Map<String, Window> keys;

    public FixedWindowRateLimiter(int maxRequests, int windowSize) {
        this.maxRequests = maxRequests;
        this.windowSize = windowSize;
        this.keys = new HashMap<>();
    }

    public boolean isAllowed(String key) {
        long now = getCurrentTime();
        Window window = keys.get(key);

        if (window == null || now - window.startedAt >= windowSize) {
            window = new Window(now);
            keys.put(key, window);
        }

        if (window.count >= maxRequests) {
            return false;
        }

        window.count++;
        return true;
    }

    private long getCurrentTime() {
        if (timeFixed) {
            return currentTime;
        }
        return System.currentTimeMillis();
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
        this.timeFixed = true;
    }

    private static class Window {
        private final long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
