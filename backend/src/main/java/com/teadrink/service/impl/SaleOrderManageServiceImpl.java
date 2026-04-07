package com.teadrink.service.impl;

import com.teadrink.common.BusinessException;
import com.teadrink.mapper.SaleOrderManageMapper;
import com.teadrink.service.SaleOrderManageService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SaleOrderManageServiceImpl implements SaleOrderManageService {

    @Resource
    private SaleOrderManageMapper saleOrderManageMapper;

    @Override
    public Map<String, Object> page(int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, 50);
        int safePage = page <= 0 ? 1 : page;

        long total = nvlLong(saleOrderManageMapper.countAll());
        int pages = (int) ((total + safeSize - 1) / safeSize);
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> list = saleOrderManageMapper.listPage(offset, safeSize);

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("list", list);
        res.put("page", safePage);
        res.put("size", safeSize);
        res.put("pages", Math.max(pages, 1));
        return res;
    }

    @Override
    public Map<String, Object> detail(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BusinessException("订单号不能为空");
        }
        Map<String, Object> base = saleOrderManageMapper.getDetailBase(orderNo.trim());
        if (base == null || base.isEmpty()) {
            throw new BusinessException("订单不存在");
        }
        List<Map<String, Object>> items = saleOrderManageMapper.listDetailItems(orderNo.trim());
        base.put("items", items);
        return base;
    }

    private static long nvlLong(Long v) {
        return v == null ? 0L : v;
    }
}

