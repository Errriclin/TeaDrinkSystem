package com.teadrink.service.impl;

import com.teadrink.mapper.DashboardMapper;
import com.teadrink.service.DashboardService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private DashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> summary() {
        BigDecimal todayRevenue = nvl(dashboardMapper.sumTodayRevenue());
        BigDecimal yesterdayRevenue = nvl(dashboardMapper.sumYesterdayRevenue());
        Integer todayOrders = nvlInt(dashboardMapper.countTodayOrders());
        Integer yesterdayOrders = nvlInt(dashboardMapper.countYesterdayOrders());
        Integer newMembers = nvlInt(dashboardMapper.countTodayNewMembers());
        Integer totalMembers = nvlInt(dashboardMapper.countTotalMembers());
        Integer lowStockCount = nvlInt(dashboardMapper.countLowStockMaterials());

        Map<String, Object> data = new HashMap<>();
        data.put("todayRevenue", todayRevenue);
        data.put("revenueGrowth", growthPercent(todayRevenue, yesterdayRevenue));
        data.put("todayOrders", todayOrders);
        data.put("ordersGrowth", growthPercent(new BigDecimal(todayOrders), new BigDecimal(yesterdayOrders)));
        data.put("newMembers", newMembers);
        data.put("totalMembers", totalMembers);
        data.put("lowStockCount", lowStockCount);
        return data;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Integer nvlInt(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 返回整数百分比（四舍五入），用于前端展示：xx%
     * 若 yesterday 为 0，则返回 0（避免 Infinity）。
     */
    private static Integer growthPercent(BigDecimal today, BigDecimal yesterday) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (today == null) {
            today = BigDecimal.ZERO;
        }
        BigDecimal diff = today.subtract(yesterday);
        BigDecimal pct = diff.multiply(new BigDecimal("100")).divide(yesterday, 0, RoundingMode.HALF_UP);
        return pct.intValue();
    }
}

