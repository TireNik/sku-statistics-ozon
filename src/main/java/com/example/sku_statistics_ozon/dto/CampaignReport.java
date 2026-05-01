package com.example.sku_statistics_ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignReport {
    private List<ProductStatRow> rows;
    private ProductStatRow totals;
}