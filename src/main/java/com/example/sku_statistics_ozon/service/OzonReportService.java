package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;

import java.util.List;

public interface OzonReportService {
    List<EnrichedCampaignRow> buildReport(String dateFrom, String dateTo, String token);
}
