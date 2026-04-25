package com.example.sku_statistics_ozon.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportTotals {
    private String views;
    private String clicks;
    private String moneySpent;
    private String orders;
    private String ordersMoney;
    private String drr;
}