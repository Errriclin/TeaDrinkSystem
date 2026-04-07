package com.teadrink.service.impl;

import com.teadrink.mapper.ReportMapper;
import com.teadrink.service.ReportService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private ReportMapper reportMapper;

    @Override
    public Map<String, Object> revenueTrend(int days) {
        int safeDays = days <= 0 ? 7 : Math.min(days, 60);

        List<Map<String, Object>> rows = reportMapper.revenueTrend(safeDays);
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object d = row.get("sale_date");
            Object v = row.get("revenue");
            if (d == null) {
                continue;
            }
            LocalDate date = toLocalDate(d);
            byDate.put(date, toBigDecimal(v));
        }

        List<String> labels = new ArrayList<>();
        List<BigDecimal> data = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < safeDays; i++) {
            LocalDate cur = start.plusDays(i);
            labels.add(cur.format(fmt));
            data.add(byDate.getOrDefault(cur, BigDecimal.ZERO));
        }

        Map<String, Object> res = new HashMap<>();
        res.put("labels", labels);
        res.put("data", data);
        return res;
    }

    private static LocalDate toLocalDate(Object v) {
        if (v instanceof java.sql.Date) {
            return ((java.sql.Date) v).toLocalDate();
        }
        if (v instanceof java.time.LocalDate) {
            return (LocalDate) v;
        }
        if (v instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) v).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v));
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(String.valueOf(v));
        }
        return new BigDecimal(String.valueOf(v));
    }
}

