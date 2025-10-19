package com.memora.executors;

import com.memora.core.MemoraNode;
import com.memora.enums.NodeType;
import com.memora.messages.InfoCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.InfoCommand.BucketInfoRequest;
import com.memora.messages.InfoCommand.ClusterInfoRequest;
import com.memora.messages.InfoCommand.NodeInfoRequest;
import com.memora.utils.Parser;

public class InfoExecutor extends Executor {
    private final MemoraNode node;


    public InfoExecutor(MemoraNode node) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        InfoCommand command = request.getInfoCommand();

        return switch (command.getAboutCase()) {
            case NODE_INFO -> {
                NodeInfoRequest nodeInfoRequest = command.getNodeInfo();

                yield switch (nodeInfoRequest.getType()) {
                    case ID -> OK(request, MemoraNode.getInfo().getNodeId());
                    case ALL -> OK(request, Parser.toJson(MemoraNode.getInfo()));
                    case MAX_QPS -> OK(request, String.valueOf(MemoraNode.getInfo().getMaxQps()));
                    case CURRENT_QPS -> OK(request, String.valueOf(MemoraNode.getInfo().getCurrentQps()));
                    default -> UNSUPPORTED_OPERATION(request, "Invalid sub-command for InfoCommand " + nodeInfoRequest.getType());
                };
            }

            case BUCKET_INFO -> {
                BucketInfoRequest bucketInfoRequest = command.getBucketInfo();

                yield switch (bucketInfoRequest.getType()) {
                    case MAP -> OK(request, Parser.toJson(node.getAllBuckets()));
                    default -> UNSUPPORTED_OPERATION(request, "Invalid sub-command for InfoCommand " + bucketInfoRequest.getType());
                };
            }

            case CLUSTER_INFO -> {
                if (NodeType.STANDALONE.equals(MemoraNode.getInfo().getType())) yield UNSUPPORTED_OPERATION(request, "Node is standalone");
                
                ClusterInfoRequest clusterInfoRequest = command.getClusterInfo();
                yield switch (clusterInfoRequest.getType()) {
                    case MAP -> OK(request, Parser.toJson(node.getClusterMap()));
                    default -> UNSUPPORTED_OPERATION(request, "Invalid sub-command for InfoCommand " + clusterInfoRequest.getType());
                };
            }

            default -> UNSUPPORTED_OPERATION(request);
        };
    }
}
