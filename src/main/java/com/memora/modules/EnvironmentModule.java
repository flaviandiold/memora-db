package com.memora.modules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import com.memora.utils.ULID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EnvironmentModule {
    private static String nodeId = null;

    public static String getNodeId() {
        if (Objects.isNull(nodeId)) {
            nodeId = ULID.generate();
        }
        return nodeId;
    }

    public static String getHost() {
        String host = getEnv("NODE_HOST");
        if (!Objects.isNull(host)) return host;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to get local host address", e);
        }
    }

    public static int getPort() {
        return Integer.parseInt(getOrDefault("NODE_PORT", "9090"));
    }

    private static String getOrDefault(String key, String value) {
        String env = getEnv(key);
        if (Objects.isNull(env)) return value;
        return env;
    }

    public static int getNumberOfBuckets() {
        String numberOfBuckets = getEnv("NUMBER_OF_BUCKETS");
        if (!Objects.isNull(numberOfBuckets)) return Integer.parseInt(numberOfBuckets);
        double freeMemory = Runtime.getRuntime().freeMemory();
        int bucketsForMemory = (int) Math.ceil(freeMemory / (1024 * 1024 * 500));
        if (bucketsForMemory <= 1) return 3;
        if (bucketsForMemory > 10) return 10;
        return bucketsForMemory;
    }

    private static String getRequiredEnv(String key) {
        String val = System.getenv(key);
        if (Objects.isNull(val)) throw new IllegalStateException(key + " not found in environment");
        return val;
    }

    private static String getEnv(String key) {
        return System.getenv(key);
    }
}