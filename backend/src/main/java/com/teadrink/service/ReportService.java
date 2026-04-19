package com.teadrink.service;

import java.util.List;
import java.util.Map;

public interface ReportService {
    Map<String, Object> revenueTrend(int days);

    /** 商品销售排行，limit=10 时走 Redis 缓存 */
    List<Map<String, Object>> productRank(int limit);

    /** 清除 Top10 商品排行缓存（下单/取消后调用） */
    void evictProductRankCache();

    /** 清除收入趋势缓存（下单/取消后调用） */
    void evictRevenueTrendCache();
}

