package com.mathematics.judge;

public class GraderException extends RuntimeException {

    public GraderException(String message) {
        super(message);
    }

    public GraderException(String message, Throwable cause) {
        super(message, cause);
    }
}
