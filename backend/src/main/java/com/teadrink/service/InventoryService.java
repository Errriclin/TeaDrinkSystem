package com.teadrink.service;

import com.teadrink.dto.StockAdjustRequest;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    List<Map<String, Object>> materialList();

    List<Map<String, Object>> recentLogs(int limit);

    void adjustStock(Long materialId, StockAdjustRequest request);
}

