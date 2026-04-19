package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.dto.LoginResponse;
import com.teadrink.dto.RegisterRequest;
import com.teadrink.entity.User;
import com.teadrink.mapper.UserMapper;
import com.teadrink.service.AuthService;
import com.teadrink.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TokenService tokenService;

    @Value("${app.auth.dashboard-path:/html/Mainwindow.html}")
    private String dashboardPath;

    @Override
    public LoginResponse login(String phone, String password) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            return LoginResponse.fail("请输入手机号和密码");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone.trim()));
        if (user == null) {
            return LoginResponse.fail("用户不存在或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return LoginResponse.fail("账号已停用");
        }
        if (!passwordMatches(user.getPassword(), password)) {
            return LoginResponse.fail("用户不存在或密码错误");
        }
        String token = tokenService.createToken(user.getId());
        String redirectUrl = dashboardPath + "?token=" + token;
        return LoginResponse.ok("登录成功", token, redirectUrl);
    }

    /**
     * 与 init_data 明文密码兼容；若以 $2a$ 开头则按 BCrypt 校验（便于后续迁移）。
     */
    private boolean passwordMatches(String stored, String raw) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$")) {
            try {
                return BCrypt.checkpw(raw, stored);
            } catch (Exception e) {
                return false;
            }
        }
        return stored.equals(raw);
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.getPhone()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("手机号和密码不能为空");
        }
        String phone = request.getPhone().trim();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已注册");
        }
        User u = new User();
        u.setPhone(phone);
        u.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        u.setName("");
        u.setRole("CASHIER");
        u.setStatus(1);
        userMapper.insert(u);
        if (u.getId() == null) {
            throw new BusinessException("注册失败，请稍后重试");
        }
        String token = tokenService.createToken(u.getId());
        String redirectUrl = dashboardPath + "?token=" + token;
        return LoginResponse.ok("注册成功", token, redirectUrl);
    }
}
