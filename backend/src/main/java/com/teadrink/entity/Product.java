package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private String productTag;
    private BigDecimal salePrice;
    private Integer status;
    private String imageUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
