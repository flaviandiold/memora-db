package com.memora.model;

import com.memora.enums.ClusterState;

public class ClusterInfo {
    private static long epoch;
    private static ClusterState state;

    static {
        epoch = -1L;
        state = ClusterState.ACTIVE;
    }

    public static long getEpoch() {
        return epoch;
    }

    public static ClusterState getState() {
        return state;
    }

    public static void setEpoch(long newEpoch) {
        epoch = newEpoch;
    }

    public static void incrementEpoch() {
        epoch++;
    }

    public static void setState(ClusterState newState) {
        state = newState;
    }

}
