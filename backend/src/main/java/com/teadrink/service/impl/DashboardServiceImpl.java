package com.teadrink.service.impl;

import com.teadrink.mapper.DashboardMapper;
import com.teadrink.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private static final String CACHE_KEY = "dashboard:summary";
    private static final long CACHE_TTL_SECONDS = 30;

    @Resource
    private DashboardMapper dashboardMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> summary() {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof Map) {
                log.info("[Redis] 命中工作台汇总缓存");
                return (Map<String, Object>) cached;
            }
        } catch (Exception e) {
            log.warn("[Redis] 读取 dashboard 缓存失败，降级查库: {}", e.getMessage());
        }

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

        try {
            redisTemplate.opsForValue().set(CACHE_KEY, data, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[Redis] 工作台汇总已写入缓存，TTL={}s", CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Redis] 写入 dashboard 缓存失败: {}", e.getMessage());
        }
        return data;
    }

    @Override
    public void evictSummaryCache() {
        try {
            Boolean deleted = redisTemplate.delete(CACHE_KEY);
            log.info("[Redis] 清除工作台汇总缓存: key={}, deleted={}", CACHE_KEY, deleted);
        } catch (Exception e) {
            log.warn("[Redis] 清除 dashboard 缓存失败: {}", e.getMessage());
        }
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
