package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import com.example.sku_statistics_ozon.dto.ReportCsvRow;

import java.util.List;

public interface CsvExportService {
    byte[] exportToCsv(List<EnrichedCampaignRow> rows);
}
