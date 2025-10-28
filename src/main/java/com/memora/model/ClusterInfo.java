package com.memora.model;

import com.memora.enums.ClusterState;

public class ClusterInfo {
    private static ClusterState state;

    static {
        state = ClusterState.ACTIVE;
    }

    public static ClusterState getState() {
        return state;
    }

    public static void setState(ClusterState newState) {
        state = newState;
    }

}
