package com.example.sku_statistics_ozon.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportRow {

    private String sku;
    private String title;
    private String moneySpent;

    public BigDecimal getMoneySpentValue() {
        if (moneySpent == null || moneySpent.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(moneySpent.replace(',', '.'));
    }

    public String getSku() {
        return sku != null ? sku : "";
    }

    public String getProductTitle() {
        return title != null ? title : "Без названия";
    }
}