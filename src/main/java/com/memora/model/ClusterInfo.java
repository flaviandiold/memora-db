package com.memora.model;

import java.util.concurrent.atomic.AtomicLong;

import com.memora.enums.ClusterState;

public class ClusterInfo {
    private static ClusterState state;
    private static AtomicLong lastScaleEvent;

    static {
        state = ClusterState.ACTIVE;
        lastScaleEvent = new AtomicLong(0);
    }

    public static ClusterState getState() {
        return state;
    }

    public static void setState(ClusterState newState) {
        state = newState;
    }

    public static long getLastScaleEvent() {
        return lastScaleEvent.get();
    }

    public static void setLastScaleEvent(long newEvent) {
        lastScaleEvent.set(newEvent);
    }

}
