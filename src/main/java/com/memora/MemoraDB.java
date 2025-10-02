package com.memora;

import static com.google.inject.Guice.createInjector;

import com.google.inject.Inject;
import com.google.inject.Stage;
import com.memora.core.MemoraServer;
import com.memora.modules.CommandModule;
import com.memora.modules.EnvironmentModule;
import com.memora.modules.MemoraModule;
import com.memora.modules.ServiceModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraDB {

    @Inject
    public MemoraDB(MemoraServer server) {
        initiate(server);
    }

    public static void main(String[] args) {
        try {
            createInjector(
                    Stage.PRODUCTION,
                    new EnvironmentModule(),
                    new MemoraModule(),
                    new CommandModule(),
                    new ServiceModule()
            );
    
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Error starting MemoraDB: {}", e.getMessage());
        }
    }

    private void initiate(MemoraServer server) {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } catch (Exception e) {
                    log.error("Error during node shutdown: {}", e.getMessage());
                }
            }));

            log.info("Starting MemoraDB...");
            server.start();
            log.info("MemoraDB started successfully.");
        } catch (InterruptedException e) {
            log.error("MemoraDB startup interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error starting MemoraDB: {}", e.getMessage());
        }
    }
}
