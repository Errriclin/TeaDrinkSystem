package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_member_account_log")
public class MemberAccountLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String bizType;
    private BigDecimal deltaBalance;
    private Integer deltaPoints;
    private BigDecimal balanceAfter;
    private Integer pointsAfter;
    private Long refSaleOrderId;
    private String remark;
    private LocalDateTime createdAt;
}

