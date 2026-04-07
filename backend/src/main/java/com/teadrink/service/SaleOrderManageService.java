package com.teadrink.service;

import java.util.Map;

public interface SaleOrderManageService {
    Map<String, Object> page(int page, int size);

    Map<String, Object> detail(String orderNo);
}

