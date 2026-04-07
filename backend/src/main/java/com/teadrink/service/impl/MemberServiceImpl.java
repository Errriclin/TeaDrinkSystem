package com.teadrink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teadrink.common.BusinessException;
import com.teadrink.entity.Member;
import com.teadrink.entity.MemberAccountLog;
import com.teadrink.mapper.MemberDetailMapper;
import com.teadrink.mapper.MemberAccountLogMapper;
import com.teadrink.mapper.MemberMapper;
import com.teadrink.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberServiceImpl implements MemberService {

    @Resource
    private MemberMapper memberMapper;
    @Resource
    private MemberAccountLogMapper memberAccountLogMapper;
    @Resource
    private MemberDetailMapper memberDetailMapper;

    @Override
    public Member getByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Member> q = new LambdaQueryWrapper<>();
        q.eq(Member::getPhone, phone.trim());
        q.last("LIMIT 1");
        return memberMapper.selectOne(q);
    }

    @Override
    public List<Member> listAll() {
        LambdaQueryWrapper<Member> q = new LambdaQueryWrapper<>();
        q.orderByDesc(Member::getCreatedAt).orderByDesc(Member::getId);
        return memberMapper.selectList(q);
    }

    @Override
    public Member create(Member member) {
        if (member == null) {
            return null;
        }
        // 最小化兜底：前端会传 phone/name/level/balance
        if (member.getLevel() == null) {
            member.setLevel(1);
        }
        if (member.getBalance() == null) {
            member.setBalance(BigDecimal.ZERO);
        }
        if (member.getPoints() == null) {
            member.setPoints(0);
        }
        if (member.getTotalConsume() == null) {
            member.setTotalConsume(BigDecimal.ZERO);
        }
        if (member.getStatus() == null) {
            member.setStatus(1);
        }
        memberMapper.insert(member);
        return memberMapper.selectById(member.getId());
    }

    @Override
    public List<Member> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isEmpty()) {
            return listAll();
        }
        LambdaQueryWrapper<Member> q = new LambdaQueryWrapper<>();
        q.and(w -> w.like(Member::getPhone, k).or().like(Member::getName, k));
        q.orderByDesc(Member::getCreatedAt).orderByDesc(Member::getId);
        return memberMapper.selectList(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Member recharge(Long memberId, BigDecimal amount, String remark) {
        if (memberId == null) {
            throw new BusinessException("会员ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("会员不存在");
        }
        if (member.getStatus() != null && member.getStatus() == 0) {
            throw new BusinessException("会员已冻结，无法充值");
        }

        BigDecimal before = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal after = before.add(amount);
        member.setBalance(after);
        memberMapper.updateById(member);

        MemberAccountLog log = new MemberAccountLog();
        log.setMemberId(member.getId());
        log.setBizType("RECHARGE");
        log.setDeltaBalance(amount);
        log.setDeltaPoints(0);
        log.setBalanceAfter(after);
        log.setPointsAfter(member.getPoints() == null ? 0 : member.getPoints());
        log.setRefSaleOrderId(null);
        log.setRemark((remark == null || remark.trim().isEmpty()) ? "会员充值" : remark.trim());
        log.setCreatedAt(LocalDateTime.now());
        memberAccountLogMapper.insert(log);

        return memberMapper.selectById(memberId);
    }

    @Override
    public Map<String, Object> detail(Long memberId) {
        if (memberId == null) {
            throw new BusinessException("会员ID不能为空");
        }
        Map<String, Object> base = memberDetailMapper.base(memberId);
        if (base == null || base.isEmpty()) {
            throw new BusinessException("会员不存在");
        }
        Map<String, Object> data = new HashMap<>(base);
        data.put("recent_account_logs", memberDetailMapper.recentAccountLogs(memberId, 20));
        data.put("recent_orders", memberDetailMapper.recentOrders(memberId, 10));
        return data;
    }
}

