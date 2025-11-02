# MemoraDB

![MemoraDB](https://img.shields.io/badge/MemoraDB-In--Memory%20Distributed%20DB-blue)
![Java](https://img.shields.io/badge/Java-17+-orange)
![Netty](https://img.shields.io/badge/Netty-Networking-brightgreen)
![Protobuf](https://img.shields.io/badge/Protobuf-RPC-blueviolet)

MemoraDB is a high-performance, distributed, in-memory key-value database designed for scalability and high availability. It uses a primary-replica architecture, automatic data sharding, and a gossip protocol for cluster management, making it suitable for demanding caching and data storage scenarios.

## Table of Contents

- [Core Concepts](#core-concepts)
- [Features](#features)
- [Architecture](#architecture)
  - [Communication Protocol](#communication-protocol)
  - [Data Sharding (Buckets)](#data-sharding-buckets)
  - [Replication & Consistency](#replication--consistency)
  - [Cluster Management](#cluster-management)
- [Getting Started](#getting-started)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Using the CLI](#using-the-cli)
- [Command Reference](#command-reference)
  - [Data Commands](#data-commands)
  - [Cluster Commands](#cluster-commands)
  - [Node Commands](#node-commands)
  - [Info Commands](#info-commands)

---

## Core Concepts

*   **Node**: A single instance of a MemoraDB server. A node can act as a `PRIMARY`, `REPLICA`, or `STANDALONE`.
*   **Primary**: A node that is the authoritative source for a subset of the data. It handles all write operations for its data and coordinates replication to its replicas.
*   **Replica**: A node that maintains a copy of a primary's data. It can serve read requests to reduce the load on the primary but forwards all write requests to its primary.
*   **Cluster**: A group of interconnected nodes working together to store and serve data.
*   **Bucket**: The basic unit of data sharding. The entire keyspace is divided into a fixed number of buckets, and each primary is responsible for a subset of these buckets.
*   **Epoch**: A version number for the cluster's state. It's incremented whenever the cluster topology (e.g., nodes, bucket distribution) changes, ensuring that all nodes have a consistent view.

## Features

*   **Distributed & Scalable**: Scale your cluster horizontally by adding or removing nodes. The database automatically redistributes data to balance the load.
*   **High Availability**: The primary-replica model ensures that if a primary node fails, one of its replicas can be promoted to take its place, minimizing downtime (TODO).
*   **Data Sharding**: Automatic sharding of keys across cluster primaries using Jump Consistent Hash for balanced data distribution.
*   **Batch Operations**: Efficiently execute multiple `GET`, `PUT`, or `DELETE` operations in a single request.
*   **Time-To-Live (TTL)**: Set an expiry time on keys, either as a relative duration (`EX`) or an absolute timestamp (`EXAT`).
*   **Asynchronous I/O**: Built on Netty for a high-performance, non-blocking network layer.
*   **Efficient RPC**: Uses Google Protocol Buffers for a fast and compact binary communication protocol.
*   **Cluster Introspection**: A rich `INFO` command to inspect the state of nodes, data distribution, and the overall cluster.

## Architecture

### Communication Protocol

Node-to-node and client-to-node communication is handled via a custom RPC protocol built on TCP. Messages are defined using **Protocol Buffers**, ensuring efficient serialization and cross-language compatibility. The network layer is managed by **Netty**, providing a robust and highly performant asynchronous event-driven framework.

### Data Sharding (Buckets)

The keyspace is partitioned into a configurable number of "buckets". When a key is written, it's hashed to a specific bucket. The cluster maintains a `ClusterMap` which maps every bucket to a primary node. This allows any node to determine which primary is responsible for a given key and forward the request if necessary.

### Replication & Consistency

MemoraDB uses an in-memory **Write-Ahead Log (WAL)** for consistency, not durability. When a primary processes a write, it logs the operation to its WAL. Replicas then pull these changes from the primary's WAL to stay in sync. A write is only considered complete after it has been successfully replicated to a quorum of replicas.

### Cluster Management

Cluster state and health are managed via a **gossip protocol**. Nodes periodically exchange heartbeat messages containing information about themselves and other nodes they know about. This decentralized approach allows for robust failure detection. If a primary is detected as dead by a quorum of its replicas, they will automatically elect a new primary to take over.

Scaling the cluster up or down is an orchestrated process that ensures data is safely migrated from departing nodes or to new nodes before the cluster topology is officially changed.

## Getting Started

### Running with Docker Compose

The easiest way to run a multi-node MemoraDB cluster is with the provided `docker-compose.yml` file. This will launch a 5 standalone nodes.

```bash
./start.sh
```

The nodes will be available at:
*   `node-1`: `localhost:9090`
*   `node-2`: `localhost:9091`
*   `node-3`: `localhost:9092`
*   `node-4`: `localhost:9093`
*   `node-5`: `localhost:9094`

### Using the CLI

MemoraDB includes an interactive command-line interface (CLI) to connect to a node and execute commands.

```bash
docker exec -it memora-node-x "memora-cli"
```

You will be greeted with a prompt:

```
Welcome to Memora CLI!
Type 'exit' to quit.
>
```

Now you can start sending commands to the database.

```
> PUT mykey 'hello world'
status: OK

> GET mykey
status: OK
response: "hello world"
```

---

## Command Reference

This section details all available commands in MemoraDB.

### Data Commands

#### `GET <key> [<key2> ...]`
Retrieves the value for one or more keys.
- If a key doesn't exist, it is omitted from the result.
- If multiple keys are requested, the response will be a list of values.

**Example:**
`GET user:100 session:abc`

#### `PUT <key> '<value>' [EX <seconds> | EXAT <timestamp>] [<key2> '<value2>' ...]`
Sets the value for one or more key-value pairs. Values containing spaces must be enclosed in single or double quotes.
- `EX <seconds>`: Sets an expiry time in seconds from now.
- `EXAT <timestamp>`: Sets an expiry time as a Unix timestamp in milliseconds.
- An expiry option applies only to the key-value pair immediately preceding it.

**Examples:**
`PUT name 'John Doe'`
`PUT session:xyz 'some-token' EX 3600`
`PUT user:1 'active' EX 3600 user:2 'inactive' EXAT 1672531199000`

#### `DELETE <key> [<key2> ...]`
Deletes one or more keys.

**Example:**
`DELETE temp:key1 temp:key2`

### Cluster Commands

These commands manage the overall cluster topology and are typically forwarded to and executed by the cluster leader.

#### `CLUSTER NODE ADD <host@port> [<host2@port2> ...] [PRIMARY|REPLICA]`
Adds one or more new nodes to the cluster.
- **Modifier**:
  - `REPLICA` (default): New nodes are added as potential replicas, to be assigned by the cluster orchestrator.
  - `PRIMARY`: The cluster will attempt to promote one of the new nodes to a primary and assign others as its replicas, triggering a data redistribution.

**Example:**
`CLUSTER NODE ADD node-6@9090 node-7@9090`

#### `CLUSTER NODE REMOVE <nodeId> [<nodeId2> ...]`
Removes one or more nodes from the cluster. This is a graceful removal that involves migrating data off the nodes before they are shut down.

**Example:**
`CLUSTER NODE REMOVE 01H8X... 01H8Y...`

### Node Commands

These commands alter the behavior or state of the specific node they are sent to.

#### `NODE BEHAVE <PRIMARY | REPLICA | STANDALONE>`
Instructs a node to change its behavior.

**Example:**
`NODE BEHAVE PRIMARY`

#### `NODE PRIMARIZE [<replica_host@port> ...]`
Instructs a `STANDALONE` node to become a `PRIMARY` and assign the specified nodes as its replicas.

**Example:**
`NODE PRIMARIZE replica1@9090 replica2@9090`

#### `NODE REPLICATE SOURCE <primary_host@port>`
Instructs a node to become a replica of the specified primary.

**Example:**
`NODE REPLICATE SOURCE primary-node@9090`

### Info Commands

These commands provide detailed information about the state of the node and cluster.

#### `INFO NODE <TYPE>`
Retrieves information about the node receiving the command.
- **TYPE**:
  - `ID`: The unique ID of the node.
  - `TYPE`: The node's current role (`PRIMARY`, `REPLICA`, `STANDALONE`).
  - `KEYS`: A JSON list of all keys stored on the node.
  - `REPLICAS`: A JSON object of replicas managed by this primary.
  - `PRIMARIES`: The number of primaries this node knows about.
  - `REPLICAS_COUNT`: The number of replicas this primary has.
  - `ALL`: A JSON object containing all information about the node.
  - `MAX_QPS`: The configured maximum queries per second for the node.
  - `CURRENT_QPS`: The current measured queries per second.

**Example:**
`INFO NODE TYPE`

#### `INFO BUCKET <TYPE>`
Retrieves information about data sharding.
- **TYPE**:
  - `MAP`: A JSON object showing the full mapping of buckets to primary nodes.
  - `IDS`: A JSON list of bucket IDs managed by the local node.

**Example:**
`INFO BUCKET MAP`

#### `INFO CLUSTER <TYPE>`
Retrieves information about the entire cluster.
- **TYPE**:
  - `MAP`: A JSON object representing the `ClusterMap`, containing all known nodes, their roles, and states.

**Example:**
`INFO CLUSTER MAP`


## Performance

### Max QPS
- A single node was put under a load of 10,000 QPS by 10 threads parallely.
- The node was able to handle upto the given numbers per second.

```bash
  status: OK
  response: "43640"
  correlation_id: "95d6ee12-d4b0-4214-8b75-bd74a73106ed"
```

### Latency Report
- The latency report for the above load testing

```
  Total Calls: 100000
  Average Latency: 1945.1442ms
  Max Latency: 2683ms
  Min Latency: 36ms
```