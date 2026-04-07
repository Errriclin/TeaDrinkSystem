package com.teadrink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与 Login.html 一致：success、msg、token、redirectUrl（全局 snake_case 下 redirectUrl 需显式指定）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private boolean success;
    private String msg;
    private String token;

    @JsonProperty("redirectUrl")
    private String redirectUrl;

    public static LoginResponse fail(String msg) {
        return new LoginResponse(false, msg, null, null);
    }

    public static LoginResponse ok(String msg, String token, String redirectUrl) {
        return new LoginResponse(true, msg, token, redirectUrl);
    }
}
