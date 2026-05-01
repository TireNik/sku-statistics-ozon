package com.example.sku_statistics_ozon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EnrichedCampaignRow {
    private String advSku;
    private String campaignTitle;
    private String instrument;
    private String placement;
    private String campaignId;

    private BigDecimal moneySpentOrders;
    private BigDecimal moneySpentCpc;

    private BigDecimal drr;

    private BigDecimal ordersMoneyСpc;
    private Integer ordersCpc;
    private BigDecimal ctr;
    private Integer views;
    private Integer clicks;
    private Integer toCart;
    private BigDecimal clickPrice;


    private String objectType;
    private String status;
    private BigDecimal weeklyBudget;
    private String strategy;

    private String offerId;
    private String title;
}
