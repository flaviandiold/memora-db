package com.memora.cli;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.inject.Injector;
import com.google.inject.Stage;
import com.memora.core.MemoraClient;
import com.memora.messages.RpcResponse;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;
import com.memora.modules.ClientModule;
import com.memora.modules.EnvironmentModule;
import com.memora.services.ClientManager;

import static com.google.inject.Guice.createInjector;

/**
 * Simple interactive CLI client for Memora.
 */
public class MemoraCLI {

    private static final String SELF = "memora-cli";

    private Injector injector;
    private MemoraClient client;

    private MemoraCLI() {
    }

    private void inject() {
        injector = createInjector(
            Stage.PRODUCTION,
            new ClientModule()
        );
    }

    private void createClient(String host, int port) throws IOException, InterruptedException {
        ClientManager manager = injector.getInstance(ClientManager.class);
        NodeBase base = manager.getAddress(host, port);
        client = manager.getOrCreate(NodeInfo.create(SELF, base));
    }

    public static void main(String[] args) throws Exception {
        MemoraCLI cli = new MemoraCLI();

        String host;
        int port;
        System.out.println("Memora CLI Version 1.0");
        if (args.length < 2) {
            host = EnvironmentModule.getHost();
            port = EnvironmentModule.getPort();
        } else {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }

        cli.inject();
        cli.createClient(host, port);
        if (args.length > 2 && "concurrencyTest".equalsIgnoreCase(args[2])) {
            cli.runConcurrencyTest(host, port);
        } else {
            cli.initialize(host, port);
        }

    }

    private void initialize(String host, int port) throws IOException {
        System.out.println("Welcome to Memora CLI!");
        System.out.println("Type 'exit' to quit.");

        try (Scanner scanner = new Scanner(System.in);) {
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }
                RpcResponse result = client.call(input).get();
                System.out.println(result);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        System.out.println("Exiting Memora CLI. Goodbye!");
    }

    private void runConcurrencyTest(String host, int port) {
        System.out.println("Starting concurrency test with multiple threads...");
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                System.out.println("Thread " + threadNum + " started.");
                // Each thread makes a few calls
                for (int j = 0; j < 10000; j++) {
                    String key = "key-" + threadNum + "-" + j;
                    String value = "value-" + threadNum + "-" + j;
                    client.put(key, value);
                }
            });
        }

        executor.shutdown();
        try {
            // Block until all tasks have completed execution after a shutdown request,
            // or the timeout occurs, or the current thread is interrupted.
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("Threads did not terminate in 60 seconds. Forcing shutdown...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Concurrency test finished.");
    }
}
