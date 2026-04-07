package com.teadrink.service.impl;

import com.teadrink.mapper.SaleOrderQueryMapper;
import com.teadrink.service.SaleOrderQueryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class SaleOrderQueryServiceImpl implements SaleOrderQueryService {

    @Resource
    private SaleOrderQueryMapper saleOrderQueryMapper;

    @Override
    public List<Map<String, Object>> recent(int limit) {
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 50);
        return saleOrderQueryMapper.listRecent(safeLimit);
    }
}

