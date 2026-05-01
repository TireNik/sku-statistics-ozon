package com.example.sku_statistics_ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignReportWrapper {
    private String title;
    private CampaignReport report;
}