package com.example.sku_statistics_ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportDetailResponse extends HashMap<String, CampaignReportDetail> {
}