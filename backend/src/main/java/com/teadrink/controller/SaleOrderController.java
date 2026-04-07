package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.config.TokenAuthInterceptor;
import com.teadrink.dto.SaleOrderCreateRequest;
import com.teadrink.dto.SaleOrderCreateResponse;
import com.teadrink.service.SaleOrderManageService;
import com.teadrink.service.SaleOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SaleOrderController {

    @Resource
    private SaleOrderService saleOrderService;

    @Resource
    private SaleOrderManageService saleOrderManageService;

    /**
     * POS 提交订单：POST /api/sale_order
     * 请求体字段是 snake_case；后端通过 Jackson snake_case 自动映射到 DTO。
     */
    @PostMapping("/sale_order")
    public Result<SaleOrderCreateResponse> create(@Valid @RequestBody SaleOrderCreateRequest request,
                                                 HttpServletRequest httpServletRequest) {
        Long cashierId = (Long) httpServletRequest.getAttribute(TokenAuthInterceptor.ATTR_USER_ID);
        return Result.ok(saleOrderService.create(cashierId, request));
    }

    /**
     * 订单列表（分页）
     * GET /api/sale_order/list?page=1&size=20
     * 返回：{total, list, page, size, pages}
     */
    @GetMapping("/sale_order/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(saleOrderManageService.page(page, size));
    }

    /**
     * 订单详情（含明细）
     * GET /api/sale_order/{orderNo}/detail
     */
    @GetMapping("/sale_order/{orderNo}/detail")
    public Result<Map<String, Object>> detail(@PathVariable("orderNo") String orderNo) {
        return Result.ok(saleOrderManageService.detail(orderNo));
    }

    /**
     * 取消订单（事务回补库存）
     */
    @PostMapping("/sale_order/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable("orderNo") String orderNo) {
        saleOrderService.cancelByOrderNo(orderNo);
        return Result.ok(null);
    }
}

