package com.starsbattle.common;

/**
 * Global response envelope: every successful response is
 * {@code {success:true, message, data}}; every error response is
 * {@code {success:false, message, data:null}}.
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
