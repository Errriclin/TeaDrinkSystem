package com.teadrink.service.impl;

import com.teadrink.mapper.InventoryQueryMapper;
import com.teadrink.service.InventoryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Resource
    private InventoryQueryMapper inventoryQueryMapper;

    @Override
    public List<Map<String, Object>> materialList() {
        return inventoryQueryMapper.listMaterialsWithLowFlag();
    }

    @Override
    public List<Map<String, Object>> recentLogs(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return inventoryQueryMapper.recentInventoryLogs(safeLimit);
    }
}

