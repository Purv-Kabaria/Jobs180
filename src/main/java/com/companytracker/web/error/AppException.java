package com.companytracker.web.error;

public class AppException extends RuntimeException {

    private final String code;
    private final int status;

    public AppException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public static AppException notFound(String message) {
        return new AppException("NOT_FOUND", message, 404);
    }

    public static AppException conflict(String code, String message) {
        return new AppException(code, message, 409);
    }

    public static AppException badRequest(String code, String message) {
        return new AppException(code, message, 400);
    }

    public static AppException forbidden(String code, String message) {
        return new AppException(code, message, 403);
    }

    public static AppException tooManyRequests(String message) {
        return new AppException("RATE_LIMITED", message, 429);
    }
}
