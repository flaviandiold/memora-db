package com.memora.cli;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.inject.Injector;
import com.google.inject.Stage;
import com.memora.core.MemoraClient;
import com.memora.messages.RpcResponse;
import com.memora.model.NodeBase;
import com.memora.modules.CoreServiceModule;
import com.memora.modules.EnvironmentModule;
import com.memora.services.ClientManager;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import static com.google.inject.Guice.createInjector;

/**
 * Simple interactive CLI client for Memora.
 */
public class MemoraCLI {

    private Injector injector;
    private MemoraClient client;

    private MemoraCLI() {
    }

    private void inject() {
        injector = createInjector(
            Stage.PRODUCTION,
            new CoreServiceModule()
        );
    }

    private void createClient(String host, int port) throws IOException, InterruptedException {
        ClientManager manager = injector.getInstance(ClientManager.class);
        NodeBase base = manager.getAddress(host, port);
        String nodeId = manager.createAndAdd(base);
        client = manager.getClient(nodeId);
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
            cli.runConcurrencyTest();
        } else {
            cli.initialize();
        }

    }

    private void initialize() throws IOException {
        System.out.println("Welcome to Memora CLI!");
        System.out.println("Type 'exit' to quit (or press Ctrl+D).");

        // 1. Create the terminal
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // 2. Create the LineReader with history
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .build();

        String prompt = "> ";
        
        while (true) {
            String line = "";
            try {
                line = lineReader.readLine(prompt);
            } catch (UserInterruptException e) {
                // Handle Ctrl+C (as a user interrupt, not an error)
                if (e.getPartialLine() == null || e.getPartialLine().isEmpty()) {
                    // Line was empty, so exit the program
                    // We print a newline to move off the prompt line cleanly
                    break; // Break the loop to exit
                } else {
                    // Line was NOT empty, so just cancel the current input
                    // and continue to the next loop iteration (re-prompt)
                    continue;
                }
            } catch (EndOfFileException e) {
                // Handle Ctrl+D (as a clean exit)
                break;
            }

            line = line.trim();

            if ("exit".equalsIgnoreCase(line)) {
                break;
            }

            if (line.isEmpty()) {
                continue; // Don't send empty commands
            }

            try {
                // This is your original logic for handling commands
                RpcResponse result = client.call(line).get();
                System.out.println(result);
            } catch (Exception e) {
                // Catch all other exceptions and continue
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        // 4. Clean up
        terminal.close();
        System.out.println("Exiting Memora CLI. Goodbye!");
        System.exit(0);
    }

    private void runConcurrencyTest() {
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
