package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.config.TokenAuthInterceptor;
import com.teadrink.dto.PurchaseCreateRequest;
import com.teadrink.service.PurchaseOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PurchaseOrderController {

    @Resource
    private PurchaseOrderService purchaseOrderService;

    @GetMapping("/purchase_order/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(purchaseOrderService.list());
    }

    @PostMapping("/purchase_order")
    public Result<Map<String, Object>> create(@Valid @RequestBody PurchaseCreateRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long operatorId = (Long) httpServletRequest.getAttribute(TokenAuthInterceptor.ATTR_USER_ID);
        return Result.ok(purchaseOrderService.create(operatorId, request));
    }

    @PostMapping("/purchase_order/{id}/confirm_inbound")
    public Result<Map<String, Object>> confirmInbound(@PathVariable("id") Long id,
                                                      HttpServletRequest httpServletRequest) {
        Long operatorId = (Long) httpServletRequest.getAttribute(TokenAuthInterceptor.ATTR_USER_ID);
        return Result.ok(purchaseOrderService.confirmInbound(id, operatorId));
    }

    @GetMapping("/purchase_order/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(purchaseOrderService.detail(id));
    }
}

