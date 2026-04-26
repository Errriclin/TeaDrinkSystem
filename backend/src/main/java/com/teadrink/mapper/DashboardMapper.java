package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface DashboardMapper {

    @Select("SELECT IFNULL(SUM(o.pay_amount), 0) FROM t_sale_order o WHERE o.status = 1 AND DATE(o.created_at) = CURDATE()")
    BigDecimal sumTodayRevenue();

    @Select("SELECT IFNULL(SUM(o.pay_amount), 0) FROM t_sale_order o WHERE o.status = 1 AND DATE(o.created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    BigDecimal sumYesterdayRevenue();

    @Select("SELECT COUNT(1) FROM t_sale_order o WHERE o.status = 1 AND DATE(o.created_at) = CURDATE()")
    Integer countTodayOrders();

    @Select("SELECT COUNT(1) FROM t_sale_order o WHERE o.status = 1 AND DATE(o.created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    Integer countYesterdayOrders();

    @Select("SELECT COUNT(1) FROM t_member m WHERE DATE(m.created_at) = CURDATE()")
    Integer countTodayNewMembers();

    @Select("SELECT COUNT(1) FROM t_member m")
    Integer countTotalMembers();

    @Select("SELECT COUNT(1) FROM t_material m WHERE m.status = 1 AND ( " +
            "(m.safety_stock > 0 AND m.stock_quantity <= m.safety_stock) " +
            "OR (m.safety_stock = 0 AND m.stock_quantity <= 0) " +
            ")")
    Integer countLowStockMaterials();
}

