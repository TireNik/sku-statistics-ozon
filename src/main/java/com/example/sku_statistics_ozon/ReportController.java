package com.example.sku_statistics_ozon;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import com.example.sku_statistics_ozon.dto.ReportCsvRow;
import com.example.sku_statistics_ozon.service.CsvExportService;
import com.example.sku_statistics_ozon.service.OzonReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
@Slf4j
public class ReportController {

    private final OzonReportService reportService;
    private final CsvExportService csvExportService;

    @GetMapping(value = "/csv", produces = "text/csv")
    public ResponseEntity<byte[]> getCsv(
            @RequestParam String dateFrom,  // 2026-04-01
            @RequestParam String dateTo,     // 2026-04-03
            @RequestHeader("X-Ozon-Token") String token
    ) {
        List<EnrichedCampaignRow> rows = reportService.buildReport(dateFrom, dateTo, token);
        byte[] csv = csvExportService.exportToCsv(rows);

        String filename = "ozon_report_" + dateFrom + "_" + dateTo + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
