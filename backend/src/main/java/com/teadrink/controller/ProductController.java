package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.entity.Product;
import com.teadrink.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 与 front-end：GET /api/product?status=1
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    @Resource
    private ProductService productService;

    @GetMapping("/product")
    public Result<List<Product>> list(@RequestParam(required = false) Integer status) {
        List<Product> list = productService.listOnSale(status != null ? status : 1);
        return Result.ok(list);
    }

    @GetMapping("/product/list")
    public Result<List<Product>> listAll(@RequestParam(required = false) Integer status) {
        return Result.ok(productService.listOnSale(status));
    }

    @PostMapping("/product")
    public Result<Product> create(@Valid @RequestBody Product product) {
        return Result.ok(productService.create(product));
    }

    @DeleteMapping("/product/{id}")
    public Result<Void> remove(@PathVariable("id") Long id) {
        productService.remove(id);
        return Result.ok(null);
    }

    @PutMapping("/product/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        productService.updateStatus(id, status);
        return Result.ok(null);
    }
}
