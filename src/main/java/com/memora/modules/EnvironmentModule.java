package com.memora.modules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public final class EnvironmentModule {
    private static String nodeId;
    private static Integer port;

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
        if (!Objects.isNull(port)) return port;
        return port = Integer.valueOf(getOrDefault("NODE_PORT", "9090"));
    }

    private static String getOrDefault(String key, String value) {
        String env = getEnv(key);
        if (Objects.isNull(env)) return value;
        return env;
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