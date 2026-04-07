package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    @Select("SELECT DATE(o.created_at) AS sale_date, IFNULL(SUM(o.pay_amount), 0) AS revenue " +
            "FROM t_sale_order o " +
            "WHERE o.status = 1 " +
            "  AND o.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY) " +
            "GROUP BY DATE(o.created_at) " +
            "ORDER BY sale_date ASC")
    List<Map<String, Object>> revenueTrend(@Param("days") int days);

    @Select("SELECT name, total_sold FROM v_product_rank ORDER BY total_sold DESC LIMIT #{limit}")
    List<Map<String, Object>> productRank(@Param("limit") int limit);

    @Select("SELECT sale_date, order_count, total_amount, discount_amount, real_income, cash_amount, mobile_amount " +
            "FROM v_daily_sales ORDER BY sale_date DESC LIMIT #{limit}")
    List<Map<String, Object>> dailySales(@Param("limit") int limit);
}

