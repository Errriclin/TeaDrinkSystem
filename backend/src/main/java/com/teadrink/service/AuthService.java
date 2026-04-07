package com.teadrink.service;

import com.teadrink.dto.LoginResponse;
import com.teadrink.dto.RegisterRequest;

public interface AuthService {

    LoginResponse login(String phone, String password);

    void register(RegisterRequest request);
}
