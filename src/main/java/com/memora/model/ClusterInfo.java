package com.memora.model;

import com.memora.enums.ClusterState;

public class ClusterInfo {
    private static long clusterEpoch;
    private static ClusterState clusterState;

    static {
        clusterEpoch = -1L;
        clusterState = ClusterState.ACTIVE;
    }

    public static long getClusterEpoch() {
        return clusterEpoch;
    }

    public static ClusterState getClusterState() {
        return clusterState;
    }

    public static void setClusterEpoch(long epoch) {
        clusterEpoch = epoch;
    }

    public static void incrementEpoch() {
        clusterEpoch++;
    }

    public static void setClusterState(ClusterState state) {
        clusterState = state;
    }

}
