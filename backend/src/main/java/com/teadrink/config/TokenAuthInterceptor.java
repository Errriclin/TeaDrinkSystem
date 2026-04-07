package com.teadrink.config;

import com.teadrink.service.TokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 校验请求头 X-Token；与 front-end/js/index.js 中 fetchWithToken 一致。
 */
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String HEADER_TOKEN = "X-Token";

    @Resource
    private TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER_TOKEN);
        Optional<Long> uid = tokenService.validateAndGetUserId(token);
        if (!uid.isPresent()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"msg\":\"未登录或登录已过期\"}");
            return false;
        }
        request.setAttribute(ATTR_USER_ID, uid.get());
        return true;
    }
}
