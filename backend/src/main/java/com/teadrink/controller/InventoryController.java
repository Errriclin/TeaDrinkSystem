package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

    /**
     * 库存列表（前端需要 is_low）
     * GET /api/material/list
     */
    @GetMapping("/material/list")
    public Result<List<Map<String, Object>>> materialList() {
        return Result.ok(inventoryService.materialList());
    }

    /**
     * 最近库存流水
     * GET /api/inventory_log/recent?limit=10
     */
    @GetMapping("/inventory_log/recent")
    public Result<List<Map<String, Object>>> recentLogs(@RequestParam(required = false, defaultValue = "10") int limit) {
        return Result.ok(inventoryService.recentLogs(limit));
    }
}

