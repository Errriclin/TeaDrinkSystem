package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SaleOrderQueryMapper {

    @Select("SELECT " +
            "  o.id, " +
            "  o.order_no, " +
            "  o.member_id, " +
            "  m.name AS member_name, " +
            "  m.level AS member_level, " +
            "  (SELECT GROUP_CONCAT(CONCAT(i.product_name, 'x', i.quantity) ORDER BY i.id SEPARATOR '，') " +
            "     FROM t_sale_order_item i " +
            "    WHERE i.sale_order_id = o.id) AS items_summary, " +
            "  o.pay_amount, " +
            "  o.status, " +
            "  o.created_at " +
            "FROM t_sale_order o " +
            "LEFT JOIN t_member m ON m.id = o.member_id " +
            "WHERE o.status = 1 " +
            "ORDER BY o.created_at DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> listRecent(@Param("limit") int limit);
}

