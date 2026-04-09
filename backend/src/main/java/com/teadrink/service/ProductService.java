package com.teadrink.service;

import com.teadrink.dto.ProductRecipeLineResponse;
import com.teadrink.dto.ProductRecipeSaveRequest;
import com.teadrink.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> listOnSale(Integer status);

    Product create(Product product);

    void remove(Long id);

    void updateStatus(Long id, Integer status);

    List<ProductRecipeLineResponse> getRecipe(Long productId);

    void saveRecipe(Long productId, ProductRecipeSaveRequest request);
}
