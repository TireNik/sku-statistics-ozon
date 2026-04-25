package com.example.sku_statistics_ozon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalReportRow {
    private String campaignId;
    private String campaignTitle;
    private String sku;
    private String productTitle;
    private BigDecimal skuSpent;
    private BigDecimal totalSpent;
    private String period;
}
