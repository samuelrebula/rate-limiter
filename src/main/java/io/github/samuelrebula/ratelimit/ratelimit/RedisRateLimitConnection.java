package io.github.samuelrebula.ratelimit.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import java.time.Duration;

/**
 * Lettuce connection used only for bucket state.
 *
 * <p>A refused connection, a command past {@code command-timeout}, or a dropped socket
 * fails the call. Lettuce rejects commands while the socket is down and reconnects on
 * its own, so a Redis restart does not require a process restart. Keys live only in
 * Redis; a restart without persistence starts each bucket full again.
 */
public final class RedisRateLimitConnection implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final ProxyManager<String> proxyManager;

    private RedisRateLimitConnection(
            RedisClient client,
            StatefulRedisConnection<String, byte[]> connection,
            ProxyManager<String> proxyManager
    ) {
        this.client = client;
        this.connection = connection;
        this.proxyManager = proxyManager;
    }

    public static RedisRateLimitConnection open(
            String host,
            int port,
            int database,
            String username,
            String password,
            Duration commandTimeout,
            Duration keyExpirationMargin
    ) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .withTimeout(commandTimeout)
                .withClientName("rate-limiter");
        if (password != null && !password.isBlank()) {
            if (username != null && !username.isBlank()) {
                uri.withAuthentication(username, password.toCharArray());
            } else {
                uri.withPassword(password.toCharArray());
            }
        }

        RedisClient client = RedisClient.create(uri.build());
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(commandTimeout).build())
                .timeoutOptions(TimeoutOptions.builder().fixedTimeout(commandTimeout).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        try {
            RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            StatefulRedisConnection<String, byte[]> connection = client.connect(codec);
            ProxyManager<String> proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
                    .requestTimeout(commandTimeout)
                    .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(keyExpirationMargin))
                    .build();
            return new RedisRateLimitConnection(client, connection, proxyManager);
        } catch (RuntimeException exception) {
            client.shutdown(Duration.ZERO, commandTimeout);
            throw new RateLimitStoreException("Failed to connect to Redis for rate limiting", exception);
        }
    }

    public RedisRateLimiter rateLimiter() {
        return new RedisRateLimiter(proxyManager);
    }

    public void ping() {
        connection.sync().ping();
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown(Duration.ZERO, Duration.ofSeconds(1));
    }
}
