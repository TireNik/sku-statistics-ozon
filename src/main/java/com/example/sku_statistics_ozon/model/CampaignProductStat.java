package com.example.sku_statistics_ozon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignProductStat {
    private String id;
    private String title;
    private String objectType;
    private String status;
    private String placement;
    private String weeklyBudget;
    private String budget;
    private String moneySpent;
    private String views;
    private String clicks;
    private String ctr;
    private String clickPrice;
    private String orders;
    private String ordersMoney;
    private String drr;
    private String toCart;
    private String strategy;

    public BigDecimal getMoneySpentValue() {
        if (moneySpent == null || moneySpent.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(moneySpent.replace(",", "."));
    }

    public Long getViewsValue() {
        if (views == null || views.isEmpty()) return 0L;
        return Long.parseLong(views);
    }

    public Long getClicksValue() {
        if (clicks == null || clicks.isEmpty()) return 0L;
        return Long.parseLong(clicks);
    }

    public Long getOrdersValue() {
        if (orders == null || orders.isEmpty()) return 0L;
        return Long.parseLong(orders);
    }

    public BigDecimal getOrdersMoneyValue() {
        if (ordersMoney == null || ordersMoney.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(ordersMoney.replace(',', '.'));
    }

    public boolean isActive() {
        return "running".equalsIgnoreCase(status);
    }

    public boolean hasSpent() {
        return getMoneySpentValue().compareTo(BigDecimal.ZERO) > 0;
    }
}
