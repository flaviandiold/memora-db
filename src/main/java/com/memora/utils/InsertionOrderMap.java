package com.memora.utils;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class InsertionOrderMap<K> {

    private static class Node<K> {

        final K key;
        Node<K> prev, next;

        Node(K key) {
            this.key = key;
        }
    }

    private final ConcurrentHashMap<K, Node<K>> map = new ConcurrentHashMap<>();
    private Node<K> head, tail;
    private final ReentrantLock listLock = new ReentrantLock();

    /**
     * Insert or update a key. Moves the key to the end if it already exists.
     */
    public void put(K key) {
        put(key, false);
    }

    private void put(K key, boolean isLocked) {
        map.compute(key, (k, node) -> {
            if (!Objects.isNull(node)) {
                moveToEnd(node, isLocked);
                return node;
            } else {
                Node<K> newNode = new Node<>(k);
                append(newNode, isLocked);
                return newNode;
            }
        });
    }

    public void putAll(Set<K> keys) {
        try {
            listLock.lock();
            for (K key : keys) {
                put(key, true);
            }

        } finally {
            listLock.unlock();
        }
    }

    /**
     * Returns the most recently inserted/updated key in O(1).
     */
    public K getMostRecentKey() {
        try {
            listLock.lock();
            return (tail != null) ? tail.key : null;
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Removes a key if present.
     */
    public void remove(K key) {
        remove(key, false);
    }

    private void remove(K key, boolean isLocked) {
        try {
            if (!isLocked) {
                listLock.lock();
            }
            map.computeIfPresent(key, (k, node) -> {
                if (node.prev != null) {
                    node.prev.next = node.next;
                }
                if (node.next != null) {
                    node.next.prev = node.prev;
                }
                if (node == head) {
                    head = node.next;
                }
                if (node == tail) {
                    tail = node.prev;
                }
                return null;
            });
        } finally {
            if (!isLocked) {
                listLock.unlock();
            }
        }
    }

    /**
     * Append node at the end of the linked list.
     */
    private void append(Node<K> node, boolean isLocked) {
        try {
            if (!isLocked) {
                listLock.lock();
            }
            if (tail != null) {
                tail.next = node;
                node.prev = tail;
            } else {
                head = node;
            }
            tail = node;
        } finally {
            if (!isLocked) {
                listLock.unlock();
            }
        }
    }

    /**
     * Move an existing node to the end of the linked list.
     */
    private void moveToEnd(Node<K> node, boolean isLocked) {
        try {
            if (!isLocked) {
                listLock.lock();
            }
            if (node == tail) {
                return;
            }

            // Unlink from current position
            if (node.prev != null) {
                node.prev.next = node.next;
            }
            if (node.next != null) {
                node.next.prev = node.prev;
            }
            if (node == head) {
                head = node.next;
            }

            // Append at the end
            node.prev = tail;
            node.next = null;
            if (tail != null) {
                tail.next = node;
            }
            tail = node;
        } finally {
            if (!isLocked) {
                listLock.unlock();
            }
        }
    }
}
