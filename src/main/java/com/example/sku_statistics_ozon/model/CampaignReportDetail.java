package com.example.sku_statistics_ozon.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignReportDetail {
    private String title;
    private ReportData report;
}
