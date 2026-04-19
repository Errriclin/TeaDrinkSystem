package com.teadrink.service;

import com.teadrink.dto.LoginResponse;
import com.teadrink.dto.RegisterRequest;

public interface AuthService {

    LoginResponse login(String phone, String password);

    /**
     * 注册成功返回与登录相同结构（含 token、redirectUrl），便于前端直接进入工作台。
     */
    LoginResponse register(RegisterRequest request);
}
