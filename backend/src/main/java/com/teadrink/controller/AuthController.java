package com.teadrink.controller;

import com.teadrink.common.BusinessException;
import com.teadrink.common.Result;
import com.teadrink.dto.LoginRequest;
import com.teadrink.dto.LoginResponse;
import com.teadrink.dto.RegisterRequest;
import com.teadrink.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 浏览器地址栏只能发 GET：返回如何正确调用 POST 登录的说明（不产生歧义的「系统繁忙」）。
     */
    @GetMapping("/login")
    public Result<Map<String, Object>> loginUsage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tip", "登录接口必须使用 POST + JSON，不能仅靠地址栏访问。");
        m.put("post_url", "http://127.0.0.1:8080/api/login");
        m.put("headers", "Content-Type: application/json");
        m.put("body_example", "{\"phone\":\"13800138000\",\"password\":\"123456\"}");
        m.put("browser_console", "fetch('http://127.0.0.1:8080/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({phone:'13800138000',password:'123456'})}).then(r=>r.json()).then(console.log)");
        return Result.ok(m);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            return LoginResponse.fail("请携带 JSON 请求体：{\"phone\":\"手机号\",\"password\":\"密码\"}");
        }
        return authService.login(request.getPhone(), request.getPassword());
    }

    /**
     * 与 Register.html 一致：成功 HTTP 200 + LoginResponse（success、msg、token、redirectUrl）；
     * 失败 HTTP 400 + { "message": "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            LoginResponse resp = authService.register(request);
            return ResponseEntity.ok(resp);
        } catch (BusinessException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
    }
}
