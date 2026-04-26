package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.dto.StockAdjustRequest;
import com.teadrink.entity.InventoryLog;
import com.teadrink.entity.Material;
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
    public void adjustStock(Long materialId, StockAdjustRequest request) {
        if (materialId == null) {
            throw new BusinessException("原料ID不能为空");
        }
        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        BigDecimal deltaQty = request.getDeltaQty();
        BigDecimal newSafety = request.getSafetyStock();
        boolean doStock = deltaQty != null && deltaQty.compareTo(BigDecimal.ZERO) != 0;
        boolean doSafety = newSafety != null;
        if (!doStock && !doSafety) {
            throw new BusinessException("请填写非零调整数量，和/或要保存的安全库存");
        }
        if (doSafety && newSafety.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("安全库存不能为负数");
        }
        if (doStock) {
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
            String r = request.getRemark();
            log.setTypeName(r == null || r.trim().isEmpty() ? "盘点调整" : r.trim());
            log.setCreatedAt(LocalDateTime.now());
            inventoryLogMapper.insert(log);
        }
        if (doSafety) {
            LambdaUpdateWrapper<Material> uw = new LambdaUpdateWrapper<>();
            uw.eq(Material::getId, materialId).set(Material::getSafetyStock, newSafety);
            if (materialMapper.update(null, uw) <= 0) {
                throw new BusinessException("原料不存在或无法更新");
            }
        }
    }
}

