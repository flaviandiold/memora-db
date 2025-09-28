package com.memora;


import com.memora.constants.ThreadPool;
import com.memora.core.MemoraNode;
import com.memora.services.ThreadPoolService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraDB {

    public static void main(String[] args) {    
        for (ThreadPool pool: ThreadPool.getThreadPool()) {
            if (!pool.isCluster()) {
                ThreadPoolService.createThreadPool(pool);
            }
        }

        MemoraNode.start();


        // Add a shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                MemoraNode.stop();
            } catch (Exception e) {
                log.error("Error during node shutdown: {}", e.getMessage());
            }
        }));
    }
}
