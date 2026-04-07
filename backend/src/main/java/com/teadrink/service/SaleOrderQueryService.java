package com.teadrink.service;

import java.util.List;
import java.util.Map;

public interface SaleOrderQueryService {
    List<Map<String, Object>> recent(int limit);
}

