package com.example.sku_statistics_ozon;

import com.example.sku_statistics_ozon.dto.ReportRequest;
import com.example.sku_statistics_ozon.service.ReportsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
@Slf4j
public class ReportController {

    private final ReportsService reportService;

    @PostMapping
    public ResponseEntity<byte[]> generate(@RequestBody ReportRequest request) {

        String csv = reportService.generateReportAsCsv(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.csv")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
