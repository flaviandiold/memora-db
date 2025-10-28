package com.memora.utils;

import com.google.protobuf.ByteString;
import com.memora.constants.Constants;
import com.memora.enums.Operations;
import com.memora.messages.ClusterCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand.AddNodesCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand.JoinNodeCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand.RemoveNodesCommand;
import com.memora.messages.InfoCommand;
import com.memora.messages.InfoCommand.BucketInfoRequest;
import com.memora.messages.InfoCommand.ClusterInfoRequest;
import com.memora.messages.InfoCommand.NodeInfoRequest;
import com.memora.messages.KeyCommand;
import com.memora.messages.KeyCommandBatch;
import com.memora.messages.NodeAddress;
import com.memora.messages.NodeCommand;
import com.memora.messages.NodeCommand.BehaveCommand;
import com.memora.messages.NodeCommand.PrimarizeCommand;
import com.memora.messages.NodeCommand.ReplicateCommand;
import com.memora.messages.NodeCommand.BehaveCommand.NodeType;
import com.memora.messages.NodeCommand.ReplicateCommand.Bucket;
import com.memora.messages.NodeCommand.ReplicateCommand.Cluster;
import com.memora.messages.NodeCommand.ReplicateCommand.Data;
import com.memora.messages.PutCommand; // Renamed for clarity from KeyValueCommand
import com.memora.messages.PutCommandBatch;
import com.memora.messages.RpcRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RequestFactory {

    // Private constructor to prevent instantiation
    private RequestFactory() {
    }

    /**
     * Parses a command string into a structured RpcRequest protobuf object.
     *
     * @param command The input string, e.g., "PUT key 'some value'" or "GET key1
     *                key2".
     * @return A fully constructed RpcRequest object.
     * @throws IllegalArgumentException if the command is malformed.
     */
    public static RpcRequest.Builder createRequest(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty.");
        }

        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("'[^']*'|\"[^\"]*\"|\\S+");
        Matcher matcher = pattern.matcher(command);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        Operations operation = Operations.valueOf(tokens.get(0).toUpperCase());
        RpcRequest.Builder requestBuilder = RpcRequest.newBuilder();

        switch (operation) {
            case GET, DELETE -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException(operation + " requires at least one key.");
                }
                KeyCommandBatch.Builder batchBuilder = KeyCommandBatch.newBuilder();
                for (int i = 1; i < tokens.size(); i++) {
                    batchBuilder.addCommands(KeyCommand.newBuilder().setKey(tokens.get(i)));
                }
                if (operation.equals(Operations.GET)) {
                    // Assuming your proto field is named get_batch
                    requestBuilder.setGetCommand(batchBuilder);
                } else {
                    // Assuming your proto field is named delete_batch
                    requestBuilder.setDeleteCommand(batchBuilder);
                }
            }
            case PUT -> {
                if (tokens.size() < 3) {
                    throw new IllegalArgumentException("PUT requires at least one key-value pair.");
                }
                PutCommandBatch.Builder batchBuilder = PutCommandBatch.newBuilder();

                int i = 1;
                while (i < tokens.size()) {
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("PUT command has an incomplete key-value pair.");
                    }
                    String key = tokens.get(i);
                    String value = unquote(tokens.get(i + 1));
                    i += 2; // Move index past key and value

                    PutCommand.Builder putCmdBuilder = PutCommand.newBuilder()
                            .setKey(key)
                            .setValue(ByteString.copyFromUtf8(value));

                    // Check for optional expiry arguments at the new index
                    if (i < tokens.size() && "EX".equalsIgnoreCase(tokens.get(i))) {
                        if (i + 1 >= tokens.size()) {
                            throw new IllegalArgumentException("Expiry time must follow 'EX'.");
                        }
                        try {
                            long seconds = Long.parseLong(tokens.get(i + 1));
                            putCmdBuilder.setExpireInSeconds(seconds);
                            i += 2; // Move index past EX and its value
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Expiry must be a valid number.");
                        }
                    }
                    // ADDED: Logic to handle EXAT (expire at timestamp)
                    else if (i < tokens.size() && "EXAT".equalsIgnoreCase(tokens.get(i))) {
                        if (i + 1 >= tokens.size()) {
                            throw new IllegalArgumentException("Timestamp must follow 'EXAT'.");
                        }
                        try {
                            long timestamp = Long.parseLong(tokens.get(i + 1));
                            putCmdBuilder.setExpireAtTimestamp(timestamp);
                            i += 2; // Move index past EXAT and its value
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Timestamp must be a valid number.");
                        }
                    }
                    batchBuilder.addCommands(putCmdBuilder.build());
                }
                // Assuming your proto field is named put_batch
                requestBuilder.setPutCommand(batchBuilder);
            }
            case NODE -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException("NODE command requires a subcommand (e.g., REPLICATE).");
                }
                NodeCommand.Builder nodeCmdBuilder = NodeCommand.newBuilder();
                String subCommand = tokens.get(1).toUpperCase();
                switch (subCommand) {
                    case "REPLICATE": {
                        if (tokens.size() < 4) {
                            throw new IllegalArgumentException("Usage: NODE REPLICATE <type> <value>");
                        }
                        String type = tokens.get(2).toUpperCase();

                        ReplicateCommand.Builder replicateBuilder = ReplicateCommand.newBuilder();
                        switch (type) {
                            case "SOURCE":
                                Data.Builder data = Data.newBuilder()
                                        .setPrimary(parseNodeAddress(tokens.get(3)));
                                replicateBuilder.setSource(data);
                                break;
                            case "BUCKET":
                                Bucket.Builder bucket = Bucket.newBuilder();
                                String nodeId = tokens.get(3);
                                bucket.setNodeId(nodeId);

                                for (int i = 4; i < tokens.size(); i++) {
                                    bucket.addBucketIds(tokens.get(i));
                                }

                                replicateBuilder.setBucket(bucket);
                                break;

                            case "CLUSTER":
                                replicateBuilder.setCluster(Cluster.newBuilder().setMap(tokens.get(3)));
                                break;
                            default:
                                break;
                        }

                        nodeCmdBuilder.setReplicate(replicateBuilder);
                        break;
                    }
                    case "PRIMARIZE":
                        PrimarizeCommand.Builder primarizeBuilder = PrimarizeCommand.newBuilder();
                        for (int i = 2; i < tokens.size(); i++) {
                            primarizeBuilder.addReplicas(parseNodeAddress(tokens.get(i)));
                        }
                        nodeCmdBuilder.setPrimarize(primarizeBuilder);
                        break;
                    case "BEHAVE":
                        if (tokens.size() < 3) {
                            throw new IllegalArgumentException("Usage: NODE BEHAVE <type>");
                        }
                        NodeType type = NodeType.valueOf(tokens.get(2).toUpperCase());
                        nodeCmdBuilder.setBehave(BehaveCommand.newBuilder().setType(type));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported NODE subcommand: " + subCommand);
                }
                requestBuilder.setNodeCommand(nodeCmdBuilder);
            }
            case INFO -> {
                if (tokens.size() < 3) {
                    throw new IllegalArgumentException("Usage: INFO <CATEGORY> <TYPE>");
                }
                InfoCommand.Builder infoCmdBuilder = InfoCommand.newBuilder();
                String category = tokens.get(1).toUpperCase();
                String type = tokens.get(2).toUpperCase();
                try {
                    switch (category) {
                        case "NODE":
                            infoCmdBuilder.setNodeInfo(NodeInfoRequest.newBuilder()
                                    .setType(NodeInfoRequest.DataType.valueOf(type)));
                            break;
                        case "BUCKET":
                            infoCmdBuilder.setBucketInfo(BucketInfoRequest.newBuilder()
                                    .setType(BucketInfoRequest.DataType.valueOf(type)));
                            break;
                        case "CLUSTER":
                            infoCmdBuilder.setClusterInfo(ClusterInfoRequest.newBuilder()
                                    .setType(ClusterInfoRequest.DataType.valueOf(type)));
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported INFO category: " + category);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Invalid INFO type '" + type + "' for category '" + category + "'");
                }
                requestBuilder.setInfoCommand(infoCmdBuilder);
            }
            case CLUSTER -> {
                if (tokens.size() < 4) {
                    throw new IllegalArgumentException("Usage: CLUSTER <TYPE> <OPERATION> ...");
                }

                ClusterCommand.Builder clusterCmdBuilder = ClusterCommand.newBuilder();

                switch (tokens.get(1).toUpperCase()) {
                    case "NODE":
                        String action = tokens.get(2).toUpperCase();
                        ClusterNodeCommand.Builder nodeCmdBuilder = ClusterNodeCommand.newBuilder();
                        switch (action) {
                            case "ADD":
                                if (tokens.size() < 4) {
                                    throw new IllegalArgumentException(
                                            "Usage: CLUSTER NODE ADD <host@port> ... [PRIMARY|REPLICA]");
                                }
                                AddNodesCommand.Builder addBuilder = AddNodesCommand.newBuilder();
                                String modifierStr = tokens.get(tokens.size() - 1).toUpperCase();
                                if (modifierStr.equals("PRIMARY") || modifierStr.equals("REPLICA")) {
                                    modifierStr = tokens.remove(tokens.size() - 1);
                                    AddNodesCommand.Modifier modifier = AddNodesCommand.Modifier.valueOf(modifierStr);
                                    addBuilder.setModifier(modifier);
                                }

                                for (int i = 3; i < tokens.size(); i++) {
                                    addBuilder.addNodes(parseNodeAddress(tokens.get(i)));
                                }
                                nodeCmdBuilder.setAddCommand(addBuilder);
                                break;

                            case "REMOVE":
                                if (tokens.size() < 4) {
                                    throw new IllegalArgumentException("Usage: CLUSTER NODE REMOVE <host@port> ...");
                                }
                                RemoveNodesCommand.Builder removeBuilder = RemoveNodesCommand.newBuilder();
                                for (int i = 3; i < tokens.size(); i++) {
                                    removeBuilder.addNodes(parseNodeAddress(tokens.get(i)));
                                }
                                nodeCmdBuilder.setRemoveCommand(removeBuilder);
                                break;

                            case "JOIN":
                                if (tokens.size() < 4) {
                                    throw new IllegalArgumentException("Usage: CLUSTER NODE JOIN <host@port>");
                                }
                                nodeCmdBuilder.setJoinCommand(
                                        JoinNodeCommand.newBuilder().setNode(parseNodeAddress(tokens.get(3))));
                                break;

                            default:
                                throw new IllegalArgumentException(
                                        "Unsupported CLUSTER NODE action: " + action + ". Must be ADD or REMOVE.");
                        }

                        clusterCmdBuilder.setNodeCommand(nodeCmdBuilder);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported CLUSTER type: " + tokens.get(1));
                }
                // Assuming RpcRequest has setClusterCommand
                requestBuilder.setClusterCommand(clusterCmdBuilder);
            }
            default ->
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }

        String correlationId = UUID.randomUUID().toString();

        return requestBuilder.setCorrelationId(correlationId);
    }

    /**
     * Parses a "host:port" string into a NodeAddress object.
     */
    private static NodeAddress parseNodeAddress(String address) {
        String[] parts = address.split(Constants.ADDRESS_DELIMITER);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid address format. Expected host@port, got '" + address + "'");
        }
        try {
            int port = Integer.parseInt(parts[1]);
            return NodeAddress.newBuilder().setHost(parts[0]).setPort(port).build();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port in address: '" + address + "'");
        }
    }

    /**
     * Helper to remove matching single or double quotes from the start and end of a
     * string.
     */
    private static String unquote(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        char firstChar = s.charAt(0);
        char lastChar = s.charAt(s.length() - 1);
        if ((firstChar == '\'' && lastChar == '\'') || (firstChar == '"' && lastChar == '"')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}