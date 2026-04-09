package com.teadrink.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    List<Map<String, Object>> materialList();

    List<Map<String, Object>> recentLogs(int limit);

    void adjustStock(Long materialId, BigDecimal deltaQty, String remark);
}

