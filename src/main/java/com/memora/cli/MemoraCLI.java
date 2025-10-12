package com.memora.cli;

import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.memora.core.MemoraClient;
import com.memora.model.RpcResponse;
import com.memora.modules.EnvironmentModule;

/**
 * Simple interactive CLI client for Memora.
 */
public class MemoraCLI {

    private MemoraCLI() {}

    public static void main(String[] args) throws Exception {
        MemoraCLI cli = new MemoraCLI();
        String host;
        int port;
        System.out.println("Memora CLI Version 1.0");
        System.out.println(Arrays.toString(args));
        if (args.length < 2) {
            host = EnvironmentModule.getHost();
            port = EnvironmentModule.getPort();
        } else {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }

        if (args.length > 2) {
            if ("concurrencyTest".equalsIgnoreCase(args[2])) {
                cli.runConcurrencyTest(host, port);
            }
        } else {
            cli.initialize(host, port);
        }

    }
    
    private void initialize(String host, int port) {
        System.out.println("Welcome to Memora CLI!");
        System.out.println("Type 'exit' to quit.");
        try (Scanner scanner = new Scanner(System.in); MemoraClient client = new MemoraClient(host, port)) {
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }
                RpcResponse result = client.call(input);
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
                try (MemoraClient client = new MemoraClient(host, port)) {
                    System.out.println("Thread " + threadNum + " started.");
                    // Each thread makes a few calls
                    for (int j = 0; j < 10000; j++) {
                        String key = "key-" + threadNum + "-" + j;
                        String value = "value-" + threadNum + "-" + j;
                        client.put(key, value);
                    }
                } catch (Exception e) {
                    System.err.println("Error in thread " + threadNum + ": " + e.getMessage());
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
