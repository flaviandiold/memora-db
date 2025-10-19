package com.memora.exceptions;

public class MemoraException extends RuntimeException {
    public MemoraException(String message) {
        super(message);
    }

    public MemoraException(String message, Throwable cause) {
        super(message, cause);
    }
}
