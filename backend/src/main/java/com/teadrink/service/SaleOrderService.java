package com.teadrink.service;

import com.teadrink.dto.SaleOrderCreateRequest;
import com.teadrink.dto.SaleOrderCreateResponse;

public interface SaleOrderService {
    SaleOrderCreateResponse create(Long cashierId, SaleOrderCreateRequest request);

    void cancelByOrderNo(String orderNo);
}

