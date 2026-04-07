package com.teadrink.service;

import java.util.Map;

public interface DashboardService {

    /**
     * 注意：该接口返回给前端的 key 必须是 todayRevenue / revenueGrowth 等“原样”字段，
     * 不能被 Jackson 的 snake_case 策略改写，因此 Controller 会直接返回 Map。
     */
    Map<String, Object> summary();
}

