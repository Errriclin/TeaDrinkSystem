package com.teadrink.service;

import com.teadrink.dto.PurchaseCreateRequest;

import java.util.List;
import java.util.Map;

public interface PurchaseOrderService {
    List<Map<String, Object>> list();

    Map<String, Object> create(Long operatorId, PurchaseCreateRequest request);

    Map<String, Object> confirmInbound(Long id, Long operatorId);

    Map<String, Object> detail(Long id);
}

