package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.entity.Product;
import com.teadrink.mapper.ProductMapper;
import com.teadrink.service.ProductService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    @Resource
    private ProductMapper productMapper;

    @Override
    public List<Product> listOnSale(Integer status) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<>();
        if (status != null) {
            q.eq(Product::getStatus, status);
        }
        q.orderByAsc(Product::getId);
        return productMapper.selectList(q);
    }

    @Override
    public Product create(Product product) {
        if (product == null || product.getName() == null || product.getName().trim().isEmpty()) {
            throw new BusinessException("商品名称不能为空");
        }
        if (product.getSalePrice() == null || product.getSalePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("售价不能小于0");
        }
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            product.setCategory("奶茶");
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getProductTag() == null || product.getProductTag().trim().isEmpty()) {
            product.setProductTag("常规商品");
        } else {
            product.setProductTag(product.getProductTag().trim());
        }
        product.setName(product.getName().trim());
        productMapper.insert(product);
        return productMapper.selectById(product.getId());
    }

    @Override
    public void remove(Long id) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        // 软删除：改为下架，避免历史订单外键/业务影响
        product.setStatus(0);
        productMapper.updateById(product);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态参数不合法");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        product.setStatus(status);
        productMapper.updateById(product);
    }
}
