package com.memora.cli;

import java.util.Scanner;

import com.memora.core.MemoraClient;
import com.memora.modules.EnvironmentModule;

/**
 * Simple interactive CLI client for Memora.
 */
public class MemoraCLI {

    private MemoraCLI() {
    }

    public static void main(String[] args) throws Exception {
        MemoraCLI cli = new MemoraCLI();
        cli.initialize();
    }
    
    private void initialize() {
        System.out.println("Welcome to Memora CLI!");
        System.out.println("Type 'exit' to quit.");
        try (Scanner scanner = new Scanner(System.in); MemoraClient client = new MemoraClient(EnvironmentModule.getHost(), EnvironmentModule.getPort())) {
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }
                String result = client.call(input);
                System.out.println(result);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        System.out.println("Exiting Memora CLI. Goodbye!");
    }
}
