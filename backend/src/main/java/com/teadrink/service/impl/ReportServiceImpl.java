package com.teadrink.service.impl;

import com.teadrink.mapper.ReportMapper;
import com.teadrink.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);
    private static final String CACHE_KEY = "report:product_rank:top10";
    private static final long CACHE_TTL_SECONDS = 300;
    private static final String REVENUE_TREND_KEY_PREFIX = "report:revenue_trend:";
    private static final long REVENUE_TREND_TTL_SECONDS = 300;

    @Resource
    private ReportMapper reportMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> revenueTrend(int days) {
        int safeDays = days <= 0 ? 7 : Math.min(days, 60);

        String cacheKey = REVENUE_TREND_KEY_PREFIX + safeDays;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Map) {
                log.info("[Redis] 命中收入趋势缓存, days={}", safeDays);
                return (Map<String, Object>) cached;
            }
        } catch (Exception e) {
            log.warn("[Redis] 读取收入趋势缓存失败，降级查库: {}", e.getMessage());
        }

        List<Map<String, Object>> rows = reportMapper.revenueTrend(safeDays);
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object d = row.get("sale_date");
            Object v = row.get("revenue");
            if (d == null) {
                continue;
            }
            LocalDate date = toLocalDate(d);
            byDate.put(date, toBigDecimal(v));
        }

        List<String> labels = new ArrayList<>();
        List<BigDecimal> data = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < safeDays; i++) {
            LocalDate cur = start.plusDays(i);
            labels.add(cur.format(fmt));
            data.add(byDate.getOrDefault(cur, BigDecimal.ZERO));
        }

        Map<String, Object> res = new HashMap<>();
        res.put("labels", labels);
        res.put("data", data);

        try {
            redisTemplate.opsForValue().set(cacheKey, res, REVENUE_TREND_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[Redis] 收入趋势已写入缓存, days={}, TTL={}s", safeDays, REVENUE_TREND_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Redis] 写入收入趋势缓存失败: {}", e.getMessage());
        }
        return res;
    }

    private static LocalDate toLocalDate(Object v) {
        if (v instanceof java.sql.Date) {
            return ((java.sql.Date) v).toLocalDate();
        }
        if (v instanceof java.time.LocalDate) {
            return (LocalDate) v;
        }
        if (v instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) v).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v));
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(String.valueOf(v));
        }
        return new BigDecimal(String.valueOf(v));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> productRank(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);

        if (safeLimit == 10) {
            try {
                Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
                if (cached instanceof List) {
                    log.info("[Redis] 命中商品排行 Top10 缓存");
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.warn("[Redis] 读取缓存失败，降级查库: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> rows = reportMapper.productRank(safeLimit);

        if (safeLimit == 10 && rows != null) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY, rows, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                log.info("[Redis] 商品排行 Top10 已写入缓存，TTL={}s", CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("[Redis] 写入缓存失败: {}", e.getMessage());
            }
        }
        return rows;
    }

    @Override
    public void evictProductRankCache() {
        try {
            Boolean deleted = redisTemplate.delete(CACHE_KEY);
            log.info("[Redis] 清除商品排行缓存: key={}, deleted={}", CACHE_KEY, deleted);
        } catch (Exception e) {
            log.warn("[Redis] 清除缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictRevenueTrendCache() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(REVENUE_TREND_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                Long removed = redisTemplate.delete(keys);
                log.info("[Redis] 清除收入趋势缓存: count={}, removed={}", keys.size(), removed);
            }
        } catch (Exception e) {
            log.warn("[Redis] 清除收入趋势缓存失败: {}", e.getMessage());
        }
    }
}

