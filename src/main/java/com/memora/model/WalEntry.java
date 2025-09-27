package com.memora.wal;

import com.memora.model.RpcRequest;

public record WalEntry(long version, RpcRequest request) {
}