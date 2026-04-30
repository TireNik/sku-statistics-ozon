package com.example.sku_statistics_ozon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EnrichedCampaignRow {
    private String campaignId;
    private String campaignTitle;
    private String objectType;
    private String status;
    private String placement;
    private BigDecimal weeklyBudget;
    private BigDecimal moneySpentCpc;
    private Integer views;
    private Integer clicks;
    private BigDecimal ctr;
    private BigDecimal clickPrice;
    private Integer ordersCpc;
    private BigDecimal ordersMoneyСpc;
    private BigDecimal drr;
    private Integer toCart;
    private String strategy;

    private String advSku;
    private String offerId;
    private String title;
    private String instrument;
    private BigDecimal moneySpentOrders;
}
