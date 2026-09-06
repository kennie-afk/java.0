package com.soko.platform;

public class Errors {

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Unauthorized extends RuntimeException {
        public Unauthorized(String message) { super(message); }
    }

    public static class Unroutable extends RuntimeException {
        public Unroutable(String message) { super(message); }
    }
}
