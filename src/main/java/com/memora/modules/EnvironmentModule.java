package com.memora.modules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.constants.Constants;
import com.memora.model.NodeBase;
import com.memora.utils.ULID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnvironmentModule extends AbstractModule {

    @Provides
    @Named(Constants.NODE_ID)
    @Singleton
    public String getNodeId() {
        return ULID.generate();
    }
    
    @Provides
    @Named(Constants.NODE_HOST)
    @Singleton
    public static String getHost() {
        String host = getEnv(Constants.NODE_HOST);
        if (!Objects.isNull(host)) return host;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to get local host address", e);
        }
    }
    
    @Provides
    @Named(Constants.NODE_PORT)
    @Singleton
    public static int getPort() {
        return Integer.parseInt(getOrDefault(Constants.NODE_PORT, Constants.DEFAULT_PORT));
    }

    @Provides
    @Named(Constants.MY_REPLICAS)
    @Singleton
    public List<NodeBase> getMyReplicas() {
        String myReplicas = getEnv(Constants.MY_REPLICAS);
        if (!Objects.isNull(myReplicas)) {
            return Arrays.stream(myReplicas.split("\\s*,\\s*"))
                .<NodeBase>map(replica -> {
                    String[] parts = replica.split(Constants.ADDRESS_DELIMITER);
                    if (parts.length == 1) {
                        return NodeBase.create(parts[0], Integer.parseInt(Constants.DEFAULT_PORT));
                    } else if (parts.length == 2) {
                        return NodeBase.create(parts[0], Integer.parseInt(parts[1]));
                    } else {
                        log.warn("Invalid replica format: {}", replica);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        }
        return Collections.emptyList();
    }

    @Provides
    @Named(Constants.NUMBER_OF_BUCKETS)
    @Singleton
    public int getNumberOfBuckets() {
        String numberOfBuckets = getEnv(Constants.NUMBER_OF_BUCKETS);
        if (!Objects.isNull(numberOfBuckets)) return Integer.parseInt(numberOfBuckets);
        double freeMemory = Runtime.getRuntime().freeMemory();
        int bucketsForMemory = (int) Math.ceil(freeMemory / (1024 * 1024 * 500));
        if (bucketsForMemory <= 1) return 3;
        if (bucketsForMemory > 10) return 10;
        return bucketsForMemory;
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