package com.memora;

import com.memora.constants.ThreadPool;
import com.memora.core.MemoraNode;
import com.memora.services.ThreadPoolService;

public class MemoraDB {

    public static void main(String[] args) {    
        for (ThreadPool pool: ThreadPool.getThreadPool()) {
            if (!pool.isCluster()) {
                ThreadPoolService.createThreadPool(pool);
            }
        }

        MemoraNode node = new MemoraNode();
        node.start();

        // Add a shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                node.stop();
            } catch (Exception e) {
                System.err.println("Error during node shutdown: " + e.getMessage());
            }
        }));
    }
}
