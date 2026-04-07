package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.mapper.ReportMapper;
import com.teadrink.service.DashboardService;
import com.teadrink.service.ReportService;
import com.teadrink.service.SaleOrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @Resource
    private SaleOrderQueryService saleOrderQueryService;

    @Resource
    private ReportService reportService;

    @Resource
    private ReportMapper reportMapper;

    /**
     * 前端直接读取 todayRevenue/revenueGrowth 等字段，因此这里不使用 Result 包装，
     * 并且返回 Map 来避免 Jackson snake_case 改写 key。
     */
    @GetMapping("/dashboard/summary")
    public Map<String, Object> summary() {
        return dashboardService.summary();
    }

    @GetMapping("/sale_order/recent")
    public Result<List<Map<String, Object>>> recentOrders(@RequestParam(required = false, defaultValue = "5") int limit) {
        return Result.ok(saleOrderQueryService.recent(limit));
    }

    @GetMapping("/reports/revenue-trend")
    public Result<Map<String, Object>> revenueTrend(@RequestParam(required = false, defaultValue = "7") int days) {
        return Result.ok(reportService.revenueTrend(days));
    }

    /**
     * 直接查询视图 v_product_rank
     */
    @GetMapping("/v_product_rank")
    public Result<List<Map<String, Object>>> productRank(@RequestParam(required = false, defaultValue = "5") int limit) {
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 50);
        return Result.ok(reportMapper.productRank(safeLimit));
    }

    /**
     * 销售日报（来自视图 v_daily_sales）
     */
    @GetMapping("/v_daily_sales")
    public Result<List<Map<String, Object>>> dailySales(@RequestParam(required = false, defaultValue = "30") int limit) {
        int safeLimit = limit <= 0 ? 30 : Math.min(limit, 180);
        return Result.ok(reportMapper.dailySales(safeLimit));
    }
}

