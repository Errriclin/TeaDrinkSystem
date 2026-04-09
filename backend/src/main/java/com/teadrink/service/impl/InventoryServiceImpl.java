package com.teadrink.service.impl;

import com.teadrink.common.BusinessException;
import com.teadrink.entity.InventoryLog;
import com.teadrink.mapper.InventoryLogMapper;
import com.teadrink.mapper.MaterialMapper;
import com.teadrink.mapper.InventoryQueryMapper;
import com.teadrink.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Resource
    private InventoryQueryMapper inventoryQueryMapper;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private InventoryLogMapper inventoryLogMapper;

    @Override
    public List<Map<String, Object>> materialList() {
        return inventoryQueryMapper.listMaterialsWithLowFlag();
    }

    @Override
    public List<Map<String, Object>> recentLogs(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return inventoryQueryMapper.recentInventoryLogs(safeLimit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(Long materialId, BigDecimal deltaQty, String remark) {
        if (materialId == null) {
            throw new BusinessException("原料ID不能为空");
        }
        if (deltaQty == null || deltaQty.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("调整数量不能为0");
        }
        if (deltaQty.compareTo(BigDecimal.ZERO) > 0) {
            materialMapper.addStock(materialId, deltaQty);
        } else {
            int ok = materialMapper.deductStockIfEnough(materialId, deltaQty.abs());
            if (ok <= 0) {
                throw new BusinessException("库存不足，无法下调该数量");
            }
        }
        BigDecimal after = materialMapper.getStockQuantity(materialId);
        InventoryLog log = new InventoryLog();
        log.setMaterialId(materialId);
        log.setChangeQty(deltaQty);
        log.setAfterStock(after == null ? BigDecimal.ZERO : after);
        log.setBizType("ADJUST");
        log.setRefId(materialId);
        log.setTypeName(remark == null || remark.trim().isEmpty() ? "盘点调整" : remark.trim());
        log.setCreatedAt(LocalDateTime.now());
        inventoryLogMapper.insert(log);
    }
}

