package com.example.sku_statistics_ozon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReportCsvRow {
    private String sku;
    private String title;
    private String instrument;
    private String placement;
    private String campaignId;
    private BigDecimal moneySpent;
    private BigDecimal drr;
    private BigDecimal salesMoney;
    private Integer orders;
    private BigDecimal ctr;
    private Integer views;
    private Integer clicks;
    private Integer toCart;
    private BigDecimal toCartConversion;
    private BigDecimal costPerOrder;
    private BigDecimal clickPrice;
}
