package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.model.ReportRequest;


public interface ReportsService {
    String generateReportAsCsv(ReportRequest request);
}

