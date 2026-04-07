package com.teadrink.service;

import java.util.List;
import java.util.Map;

public interface InventoryService {
    List<Map<String, Object>> materialList();

    List<Map<String, Object>> recentLogs(int limit);
}

