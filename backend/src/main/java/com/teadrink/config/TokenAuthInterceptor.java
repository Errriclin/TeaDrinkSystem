package com.teadrink.config;

import com.teadrink.entity.User;
import com.teadrink.mapper.UserMapper;
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
    public static final String ATTR_USER_ROLE = "currentUserRole";
    public static final String HEADER_TOKEN = "X-Token";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_CASHIER = "CASHIER";
    private static final String ROLE_WAREHOUSE = "WAREHOUSE";

    @Resource
    private TokenService tokenService;
    @Resource
    private UserMapper userMapper;

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
        User user = userMapper.selectById(uid.get());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"msg\":\"账号不存在或已停用\"}");
            return false;
        }

        String role = normalizeRole(user.getRole());
        if (!hasPermission(role, request.getMethod(), normalizePath(request))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"msg\":\"无权限访问该功能\"}");
            return false;
        }

        request.setAttribute(ATTR_USER_ID, uid.get());
        request.setAttribute(ATTR_USER_ROLE, role);
        return true;
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ROLE_CASHIER;
        }
        return role.trim().toUpperCase();
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private boolean hasPermission(String role, String method, String path) {
        if (ROLE_ADMIN.equals(role)) {
            return true;
        }
        if (ROLE_CASHIER.equals(role)) {
            return cashierAllowed(method, path);
        }
        if (ROLE_WAREHOUSE.equals(role)) {
            return warehouseAllowed(method, path);
        }
        return false;
    }

    private boolean cashierAllowed(String method, String path) {
        if (isDashboardRead(method, path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && ("/api/product".equals(path) || "/api/product/list".equals(path))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/sale_order".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/sale_order/list".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && isSaleOrderDetail(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && isSaleOrderCancel(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && ("/api/member".equals(path) || "/api/member/list".equals(path) || "/api/member/search".equals(path))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/member".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && isMemberDetail(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && ("/api/material/list".equals(path) || "/api/inventory_log/recent".equals(path))) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && isMemberRecharge(path);
    }

    private boolean warehouseAllowed(String method, String path) {
        if (isDashboardRead(method, path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && ("/api/material/list".equals(path) || "/api/inventory_log/recent".equals(path))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/material/") && path.endsWith("/adjust")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/purchase_order/list".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/purchase_order".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && isPurchaseDetail(path)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && isPurchaseInbound(path);
    }

    private boolean isDashboardRead(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        return "/api/dashboard/summary".equals(path)
                || "/api/sale_order/recent".equals(path)
                || "/api/reports/revenue-trend".equals(path)
                || "/api/v_product_rank".equals(path)
                || "/api/v_daily_sales".equals(path);
    }

    private boolean isSaleOrderDetail(String path) {
        return path.startsWith("/api/sale_order/") && path.endsWith("/detail");
    }

    private boolean isSaleOrderCancel(String path) {
        return path.startsWith("/api/sale_order/") && path.endsWith("/cancel");
    }

    private boolean isMemberDetail(String path) {
        return path.startsWith("/api/member/") && path.endsWith("/detail");
    }

    private boolean isMemberRecharge(String path) {
        return path.startsWith("/api/member/") && path.endsWith("/recharge");
    }

    private boolean isPurchaseDetail(String path) {
        return path.startsWith("/api/purchase_order/") && path.endsWith("/detail");
    }

    private boolean isPurchaseInbound(String path) {
        return path.startsWith("/api/purchase_order/") && path.endsWith("/confirm_inbound");
    }
}
