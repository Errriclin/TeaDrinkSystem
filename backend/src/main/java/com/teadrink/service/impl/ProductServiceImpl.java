package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.dto.ProductRecipeItemRequest;
import com.teadrink.dto.ProductRecipeLineResponse;
import com.teadrink.dto.ProductRecipeSaveRequest;
import com.teadrink.entity.Material;
import com.teadrink.entity.Product;
import com.teadrink.entity.ProductMaterial;
import com.teadrink.entity.SaleOrderItem;
import com.teadrink.mapper.MaterialMapper;
import com.teadrink.mapper.ProductMapper;
import com.teadrink.mapper.ProductMaterialMapper;
import com.teadrink.mapper.SaleOrderItemMapper;
import com.teadrink.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProductServiceImpl implements ProductService {

    @Resource
    private ProductMapper productMapper;
    @Resource
    private ProductMaterialMapper productMaterialMapper;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private SaleOrderItemMapper saleOrderItemMapper;

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
        // 新增商品默认下架，先配置配方后再上架，避免售卖时库存不扣减
        if (product.getStatus() == null || product.getStatus() == 1) {
            product.setStatus(0);
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
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        LambdaQueryWrapper<SaleOrderItem> orderQ = new LambdaQueryWrapper<>();
        orderQ.eq(SaleOrderItem::getProductId, id).last("LIMIT 1");
        SaleOrderItem ref = saleOrderItemMapper.selectOne(orderQ);
        if (ref != null) {
            throw new BusinessException("该商品已有关联订单，无法删除，建议下架处理");
        }
        LambdaQueryWrapper<ProductMaterial> recipeQ = new LambdaQueryWrapper<>();
        recipeQ.eq(ProductMaterial::getProductId, id);
        productMaterialMapper.delete(recipeQ);
        productMapper.deleteById(id);
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
        if (status == 1) {
            LambdaQueryWrapper<ProductMaterial> q = new LambdaQueryWrapper<>();
            q.eq(ProductMaterial::getProductId, id);
            Long recipeCount = productMaterialMapper.selectCount(q);
            if (recipeCount == null || recipeCount <= 0) {
                throw new BusinessException("商品未配置配方，无法上架");
            }
        }
        product.setStatus(status);
        productMapper.updateById(product);
    }

    @Override
    public List<ProductRecipeLineResponse> getRecipe(Long productId) {
        if (productId == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        LambdaQueryWrapper<ProductMaterial> q = new LambdaQueryWrapper<>();
        q.eq(ProductMaterial::getProductId, productId);
        List<ProductMaterial> rows = productMaterialMapper.selectList(q);
        List<ProductRecipeLineResponse> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (ProductMaterial pm : rows) {
            if (pm.getMaterialId() == null) {
                continue;
            }
            Material m = materialMapper.selectById(pm.getMaterialId());
            String name = m == null ? "" : (m.getName() == null ? "" : m.getName());
            String unit = m == null ? "" : (m.getUnit() == null ? "" : m.getUnit());
            out.add(new ProductRecipeLineResponse(
                    pm.getId(),
                    pm.getMaterialId(),
                    name,
                    unit,
                    pm.getConsumeQty()
            ));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecipe(Long productId, ProductRecipeSaveRequest request) {
        if (productId == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (request == null || request.getItems() == null) {
            throw new BusinessException("配方数据不能为空");
        }
        List<ProductRecipeItemRequest> items = request.getItems();
        Set<Long> seen = new HashSet<>();
        for (ProductRecipeItemRequest it : items) {
            if (it.getMaterialId() == null) {
                throw new BusinessException("原料不能为空");
            }
            if (it.getConsumeQty() == null || it.getConsumeQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("每份消耗量必须大于0");
            }
            if (!seen.add(it.getMaterialId())) {
                throw new BusinessException("同一原料不能重复添加");
            }
            Material m = materialMapper.selectById(it.getMaterialId());
            if (m == null) {
                throw new BusinessException("原料不存在: id=" + it.getMaterialId());
            }
        }
        LambdaQueryWrapper<ProductMaterial> del = new LambdaQueryWrapper<>();
        del.eq(ProductMaterial::getProductId, productId);
        productMaterialMapper.delete(del);
        for (ProductRecipeItemRequest it : items) {
            ProductMaterial pm = new ProductMaterial();
            pm.setProductId(productId);
            pm.setMaterialId(it.getMaterialId());
            pm.setConsumeQty(it.getConsumeQty());
            productMaterialMapper.insert(pm);
        }
    }
}
