package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String name;
    private Integer level;
    private BigDecimal balance;
    private Integer points;
    private BigDecimal totalConsume;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

