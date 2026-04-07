package com.teadrink.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与前端约定：{ "success", "msg", "data" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private boolean success;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, "ok", data);
    }

    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(true, msg, data);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(false, msg, null);
    }
}
