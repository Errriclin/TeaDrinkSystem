package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.dto.SaleOrderCreateRequest;
import com.teadrink.dto.SaleOrderCreateRequestItem;
import com.teadrink.dto.SaleOrderCreateResponse;
import com.teadrink.entity.InventoryLog;
import com.teadrink.entity.Member;
import com.teadrink.entity.MemberAccountLog;
import com.teadrink.entity.ProductMaterial;
import com.teadrink.entity.SaleOrder;
import com.teadrink.entity.SaleOrderItem;
import com.teadrink.mapper.InventoryLogMapper;
import com.teadrink.mapper.MaterialMapper;
import com.teadrink.mapper.MemberAccountLogMapper;
import com.teadrink.mapper.MemberMapper;
import com.teadrink.mapper.ProductMaterialMapper;
import com.teadrink.mapper.SaleOrderItemMapper;
import com.teadrink.mapper.SaleOrderMapper;
import com.teadrink.service.SaleOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SaleOrderServiceImpl implements SaleOrderService {

    @Resource
    private SaleOrderMapper saleOrderMapper;
    @Resource
    private SaleOrderItemMapper saleOrderItemMapper;
    @Resource
    private ProductMaterialMapper productMaterialMapper;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private InventoryLogMapper inventoryLogMapper;
    @Resource
    private MemberMapper memberMapper;
    @Resource
    private MemberAccountLogMapper memberAccountLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleOrderCreateResponse create(Long cashierId, SaleOrderCreateRequest request) {
        if (cashierId == null) {
            throw new BusinessException("未识别到收银员身份");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("购物车为空");
        }
        if (request.getPayType() == null) {
            throw new BusinessException("请选择支付方式");
        }

        // 1) 创建订单主表
        SaleOrder order = new SaleOrder();
        order.setOrderNo(genOrderNo());
        order.setMemberId(request.getMemberId());
        order.setTotalAmount(nvl(request.getTotalAmount()));
        order.setDiscountAmount(nvl(request.getDiscountAmount()));
        order.setPayAmount(nvl(request.getPayAmount()));
        order.setPayType(request.getPayType());
        order.setStatus(1);
        order.setCashierId(cashierId);
        order.setCreatedAt(LocalDateTime.now());
        saleOrderMapper.insert(order);

        // 2) 写订单明细
        for (SaleOrderCreateRequestItem it : request.getItems()) {
            if (it.getProductId() == null || it.getQuantity() == null || it.getQuantity() <= 0) {
                throw new BusinessException("商品明细参数不合法");
            }
            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrderId(order.getId());
            item.setProductId(it.getProductId());
            item.setProductName(it.getProductName() == null ? "" : it.getProductName());
            item.setUnitPrice(nvl(it.getUnitPrice()));
            item.setQuantity(it.getQuantity());
            item.setSubtotal(nvl(it.getSubtotal()));
            saleOrderItemMapper.insert(item);
        }

        // 3) 计算本单对每个原料的总消耗，并扣库存（条件更新，失败即回滚）
        Map<Long, BigDecimal> materialConsume = calcMaterialConsume(request.getItems());
        for (Map.Entry<Long, BigDecimal> e : materialConsume.entrySet()) {
            Long materialId = e.getKey();
            BigDecimal consume = e.getValue();
            if (consume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int updated = materialMapper.deductStockIfEnough(materialId, consume);
            if (updated <= 0) {
                throw new BusinessException("库存不足，无法完成下单（原料ID=" + materialId + "）");
            }

            BigDecimal after = materialMapper.getStockQuantity(materialId);
            InventoryLog log = new InventoryLog();
            log.setMaterialId(materialId);
            log.setChangeQty(consume.negate());
            log.setAfterStock(after == null ? BigDecimal.ZERO : after);
            log.setBizType("SALE_OUT");
            log.setRefId(order.getId());
            log.setTypeName("销售出库");
            log.setCreatedAt(LocalDateTime.now());
            inventoryLogMapper.insert(log);
        }

        // 4) 会员：累计消费/积分；若余额支付则扣余额并写流水
        if (order.getMemberId() != null) {
            Member member = memberMapper.selectById(order.getMemberId());
            if (member == null) {
                throw new BusinessException("会员不存在");
            }
            if (member.getStatus() != null && member.getStatus() == 0) {
                throw new BusinessException("会员已冻结");
            }

            BigDecimal pay = nvl(order.getPayAmount());
            BigDecimal totalConsume = nvl(member.getTotalConsume()).add(pay);
            int addPoints = pay.setScale(0, RoundingMode.DOWN).intValue();
            int pointsAfter = (member.getPoints() == null ? 0 : member.getPoints()) + Math.max(addPoints, 0);

            // 余额支付
            if (order.getPayType() != null && order.getPayType() == 4) {
                BigDecimal balance = nvl(member.getBalance());
                if (balance.compareTo(pay) < 0) {
                    throw new BusinessException("会员余额不足");
                }
                BigDecimal balanceAfter = balance.subtract(pay);
                member.setBalance(balanceAfter);

                MemberAccountLog log = new MemberAccountLog();
                log.setMemberId(member.getId());
                log.setBizType("ORDER_PAY");
                log.setDeltaBalance(pay.negate());
                log.setDeltaPoints(0);
                log.setBalanceAfter(balanceAfter);
                log.setPointsAfter(pointsAfter);
                log.setRefSaleOrderId(order.getId());
                log.setRemark("余额支付订单：" + order.getOrderNo());
                log.setCreatedAt(LocalDateTime.now());
                memberAccountLogMapper.insert(log);
            }

            member.setTotalConsume(totalConsume);
            member.setPoints(pointsAfter);
            memberMapper.updateById(member);
        }

        return new SaleOrderCreateResponse(order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BusinessException("订单号不能为空");
        }
        LambdaQueryWrapper<SaleOrder> qOrder = new LambdaQueryWrapper<>();
        qOrder.eq(SaleOrder::getOrderNo, orderNo.trim()).last("LIMIT 1");
        SaleOrder order = saleOrderMapper.selectOne(qOrder);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == 0) {
            throw new BusinessException("订单已取消，无需重复操作");
        }

        LambdaQueryWrapper<SaleOrderItem> qItems = new LambdaQueryWrapper<>();
        qItems.eq(SaleOrderItem::getSaleOrderId, order.getId());
        List<SaleOrderItem> items = saleOrderItemMapper.selectList(qItems);

        // 根据已售明细回推原料消耗，执行回补库存
        for (SaleOrderItem item : items) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            LambdaQueryWrapper<ProductMaterial> qPm = new LambdaQueryWrapper<>();
            qPm.eq(ProductMaterial::getProductId, item.getProductId());
            List<ProductMaterial> pms = productMaterialMapper.selectList(qPm);
            for (ProductMaterial pm : pms) {
                if (pm.getMaterialId() == null || pm.getConsumeQty() == null) {
                    continue;
                }
                BigDecimal restore = pm.getConsumeQty().multiply(new BigDecimal(item.getQuantity()));
                if (restore.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                materialMapper.addStock(pm.getMaterialId(), restore);
                BigDecimal after = materialMapper.getStockQuantity(pm.getMaterialId());

                InventoryLog log = new InventoryLog();
                log.setMaterialId(pm.getMaterialId());
                log.setChangeQty(restore);
                log.setAfterStock(after == null ? BigDecimal.ZERO : after);
                log.setBizType("SALE_CANCEL_IN");
                log.setRefId(order.getId());
                log.setTypeName("订单取消回库");
                log.setCreatedAt(LocalDateTime.now());
                inventoryLogMapper.insert(log);
            }
        }

        // 会员回退：取消订单后冲回累计消费/积分；若原支付方式为余额，则退回余额
        if (order.getMemberId() != null) {
            Member member = memberMapper.selectById(order.getMemberId());
            if (member != null) {
                BigDecimal pay = nvl(order.getPayAmount());

                BigDecimal totalConsumeBefore = nvl(member.getTotalConsume());
                BigDecimal totalConsumeAfter = totalConsumeBefore.subtract(pay);
                if (totalConsumeAfter.compareTo(BigDecimal.ZERO) < 0) {
                    totalConsumeAfter = BigDecimal.ZERO;
                }

                int rollbackPoints = pay.setScale(0, RoundingMode.DOWN).intValue();
                int pointsBefore = member.getPoints() == null ? 0 : member.getPoints();
                int pointsAfter = pointsBefore - Math.max(rollbackPoints, 0);
                if (pointsAfter < 0) {
                    pointsAfter = 0;
                }

                BigDecimal balanceBefore = nvl(member.getBalance());
                BigDecimal deltaBalance = BigDecimal.ZERO;
                BigDecimal balanceAfter = balanceBefore;
                if (order.getPayType() != null && order.getPayType() == 4) {
                    deltaBalance = pay;
                    balanceAfter = balanceBefore.add(pay);
                    member.setBalance(balanceAfter);
                }

                member.setTotalConsume(totalConsumeAfter);
                member.setPoints(pointsAfter);
                memberMapper.updateById(member);

                MemberAccountLog log = new MemberAccountLog();
                log.setMemberId(member.getId());
                log.setBizType("ORDER_CANCEL");
                log.setDeltaBalance(deltaBalance);
                log.setDeltaPoints(-Math.max(rollbackPoints, 0));
                log.setBalanceAfter(balanceAfter);
                log.setPointsAfter(pointsAfter);
                log.setRefSaleOrderId(order.getId());
                log.setRemark("取消订单回退：" + order.getOrderNo());
                log.setCreatedAt(LocalDateTime.now());
                memberAccountLogMapper.insert(log);
            }
        }

        order.setStatus(0);
        saleOrderMapper.updateById(order);
    }

    private Map<Long, BigDecimal> calcMaterialConsume(List<SaleOrderCreateRequestItem> items) {
        Map<Long, BigDecimal> consumeMap = new HashMap<>();
        for (SaleOrderCreateRequestItem it : items) {
            LambdaQueryWrapper<ProductMaterial> q = new LambdaQueryWrapper<>();
            q.eq(ProductMaterial::getProductId, it.getProductId());
            List<ProductMaterial> pms = productMaterialMapper.selectList(q);
            if (pms == null || pms.isEmpty()) {
                continue; // 没有配方就不扣原料（演示项目允许）
            }
            for (ProductMaterial pm : pms) {
                if (pm.getMaterialId() == null || pm.getConsumeQty() == null) {
                    continue;
                }
                BigDecimal add = pm.getConsumeQty().multiply(new BigDecimal(it.getQuantity()));
                consumeMap.put(pm.getMaterialId(), consumeMap.getOrDefault(pm.getMaterialId(), BigDecimal.ZERO).add(add));
            }
        }
        return consumeMap;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String genOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "SO" + ts + rnd;
    }
}

