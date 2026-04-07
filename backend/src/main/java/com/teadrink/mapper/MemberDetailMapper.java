package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MemberDetailMapper {

    @Select("SELECT id, phone, name, level, balance, points, total_consume, status, created_at, updated_at " +
            "FROM t_member WHERE id = #{memberId} LIMIT 1")
    Map<String, Object> base(@Param("memberId") Long memberId);

    @Select("SELECT biz_type, delta_balance, delta_points, balance_after, points_after, ref_sale_order_id, remark, created_at " +
            "FROM t_member_account_log WHERE member_id = #{memberId} " +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<Map<String, Object>> recentAccountLogs(@Param("memberId") Long memberId, @Param("limit") int limit);

    @Select("SELECT order_no, pay_amount, pay_type, status, created_at " +
            "FROM t_sale_order WHERE member_id = #{memberId} " +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<Map<String, Object>> recentOrders(@Param("memberId") Long memberId, @Param("limit") int limit);
}

