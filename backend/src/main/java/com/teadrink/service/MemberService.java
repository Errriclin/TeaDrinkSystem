package com.teadrink.service;

import com.teadrink.entity.Member;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MemberService {
    Member getByPhone(String phone);

    List<Member> listAll();

    Member create(Member member);

    List<Member> search(String keyword);

    Member recharge(Long memberId, BigDecimal amount, String remark);

    Map<String, Object> detail(Long memberId);
}

