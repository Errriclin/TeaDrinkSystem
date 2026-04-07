package com.teadrink.service;

import com.teadrink.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> listOnSale(Integer status);

    Product create(Product product);

    void remove(Long id);

    void updateStatus(Long id, Integer status);
}
