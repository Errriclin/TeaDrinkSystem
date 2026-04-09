package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.dto.PurchaseCreateRequest;
import com.teadrink.dto.PurchaseCreateRequestItem;
import com.teadrink.entity.InventoryLog;
import com.teadrink.entity.Material;
import com.teadrink.entity.PurchaseOrder;
import com.teadrink.entity.PurchaseOrderItem;
import com.teadrink.mapper.InventoryLogMapper;
import com.teadrink.mapper.MaterialMapper;
import com.teadrink.mapper.PurchaseOrderItemMapper;
import com.teadrink.mapper.PurchaseOrderMapper;
import com.teadrink.mapper.PurchaseQueryMapper;
import com.teadrink.service.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    @Resource
    private PurchaseQueryMapper purchaseQueryMapper;
    @Resource
    private PurchaseOrderMapper purchaseOrderMapper;
    @Resource
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private InventoryLogMapper inventoryLogMapper;

    @Override
    public List<Map<String, Object>> list() {
        return purchaseQueryMapper.listOrders();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long operatorId, PurchaseCreateRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("请添加采购原料");
        }
        PurchaseOrder po = new PurchaseOrder();
        po.setOrderNo(genPurchaseOrderNo());
        po.setSupplierName(request.getSupplier() == null ? "" : request.getSupplier().trim());
        po.setTotalAmount(request.getTotalAmount() == null ? BigDecimal.ZERO : request.getTotalAmount());
        po.setStatus(0);
        po.setOperatorId(operatorId);
        po.setCreatedAt(LocalDateTime.now());
        purchaseOrderMapper.insert(po);

        for (PurchaseCreateRequestItem it : request.getItems()) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("采购明细参数不合法");
            }
            Long materialId = resolveMaterialId(it);
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrderId(po.getId());
            item.setMaterialId(materialId);
            item.setQuantity(it.getQuantity());
            item.setUnitPrice(it.getUnitPrice() == null ? BigDecimal.ZERO : it.getUnitPrice());
            item.setSubtotal(it.getSubtotal() == null ? BigDecimal.ZERO : it.getSubtotal());
            purchaseOrderItemMapper.insert(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", po.getId());
        data.put("orderNo", po.getOrderNo());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmInbound(Long id, Long operatorId) {
        PurchaseOrder po = purchaseOrderMapper.selectById(id);
        if (po == null) {
            throw new BusinessException("采购单不存在");
        }
        if (po.getStatus() == null || po.getStatus() != 0) {
            throw new BusinessException("该采购单状态不允许入库");
        }

        LambdaQueryWrapper<PurchaseOrderItem> q = new LambdaQueryWrapper<>();
        q.eq(PurchaseOrderItem::getPurchaseOrderId, id);
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(q);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购单明细为空");
        }

        for (PurchaseOrderItem item : items) {
            materialMapper.addStock(item.getMaterialId(), item.getQuantity());
            BigDecimal after = materialMapper.getStockQuantity(item.getMaterialId());

            InventoryLog log = new InventoryLog();
            log.setMaterialId(item.getMaterialId());
            log.setChangeQty(item.getQuantity());
            log.setAfterStock(after == null ? BigDecimal.ZERO : after);
            log.setBizType("PURCHASE_IN");
            log.setRefId(id);
            log.setTypeName("采购入库");
            log.setCreatedAt(LocalDateTime.now());
            inventoryLogMapper.insert(log);
        }

        po.setStatus(1);
        po.setInboundAt(LocalDateTime.now());
        // 保留最初创建人，不覆盖 operator_id
        purchaseOrderMapper.updateById(po);

        Map<String, Object> data = new HashMap<>();
        data.put("id", po.getId());
        data.put("status", po.getStatus());
        data.put("inboundAt", po.getInboundAt());
        return data;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        Map<String, Object> base = purchaseQueryMapper.detailBase(id);
        if (base == null || base.isEmpty()) {
            throw new BusinessException("采购单不存在");
        }
        base.put("items", purchaseQueryMapper.detailItems(id));
        return base;
    }

    private static String genPurchaseOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "PO" + ts + rnd;
    }

    /**
     * 优先使用已选原料 ID；否则用手动输入的名称（匹配或新建原料）。
     */
    private Long resolveMaterialId(PurchaseCreateRequestItem it) {
        if (it.getMaterialId() != null && it.getMaterialId() > 0) {
            Material byId = materialMapper.selectById(it.getMaterialId());
            if (byId == null) {
                throw new BusinessException("原料不存在: id=" + it.getMaterialId());
            }
            return it.getMaterialId();
        }
        String name = it.getMaterialName() == null ? "" : it.getMaterialName().trim();
        String unit = it.getMaterialUnit() == null ? "" : it.getMaterialUnit().trim();
        if (name.isEmpty()) {
            throw new BusinessException("请选择原料或填写原料名称");
        }
        if (name.length() > 100) {
            throw new BusinessException("原料名称过长");
        }
        if (unit.length() > 16) {
            throw new BusinessException("原料单位过长");
        }
        LambdaQueryWrapper<Material> q = new LambdaQueryWrapper<>();
        q.eq(Material::getName, name).last("LIMIT 1");
        Material existing = materialMapper.selectOne(q);
        if (existing != null) {
            return existing.getId();
        }
        if (unit.isEmpty()) {
            throw new BusinessException("手动新增原料时请填写单位");
        }
        Material m = new Material();
        m.setName(name);
        m.setUnit(unit);
        m.setStockQuantity(BigDecimal.ZERO);
        m.setSafetyStock(BigDecimal.ZERO);
        m.setStatus(1);
        m.setUpdatedAt(LocalDateTime.now());
        materialMapper.insert(m);
        return m.getId();
    }
}

