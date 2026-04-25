package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.FinalReportRow;
import com.example.sku_statistics_ozon.dto.ReportDetailResponse;
import com.example.sku_statistics_ozon.dto.ReportRequest;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportsServiceImpl implements ReportsService {

    private final OzonPerformanceClient client;
    private static final int MAX_CAMPAIGNS_PER_REQUEST = 10;

    @Override
    public String generateReportAsCsv(ReportRequest request) {

        log.info("=== START REPORT === {} - {}", request.getStartDate(), request.getEndDate());

        List<String> campaignIds = client.getActiveCampaignIds(
                request.getToken(),
                request.getStartDate(),
                request.getEndDate()
        );

        log.info("Total campaigns: {}", campaignIds.size());

        if (campaignIds.isEmpty()) {
            return generateEmptyReport(request);
        }

        List<List<String>> chunks = partition(campaignIds, MAX_CAMPAIGNS_PER_REQUEST);
        log.info("Split into {} chunks (max {} per request)", chunks.size(), MAX_CAMPAIGNS_PER_REQUEST);

        List<FinalReportRow> result = new ArrayList<>();

        int index = 1;

        for (List<String> chunk : chunks) {

            log.info("Processing chunk {}/{} ({} campaigns)",
                    index++, chunks.size(), chunk.size());

            String uuid = client.createReportWithRetry(
                    request.getToken(),
                    chunk,
                    request.getStartDate(),
                    request.getEndDate()
            );

            log.info("UUID created: {}", uuid);

            ReportDetailResponse report =
                    client.waitReport(request.getToken(), uuid);

            mapReport(result, report, request);
        }

        log.info("Total rows collected: {}", result.size());

        return buildCsv(result, request);
    }

    private void mapReport(List<FinalReportRow> result,
                           ReportDetailResponse report,
                           ReportRequest request) {

        report.forEach((campaignId, campaign) -> {

            if (campaign.getReport() == null || campaign.getReport().getRows() == null) {
                return;
            }

            campaign.getReport().getRows().forEach(r -> {
                result.add(FinalReportRow.builder()
                        .campaignId(campaignId)
                        .campaignTitle(campaign.getTitle())
                        .sku(r.getSku())
                        .productTitle(r.getProductTitle())
                        .skuSpent(r.getMoneySpentValue())
                        .period(request.getStartDate() + " - " + request.getEndDate())
                        .build());
            });

        });
    }

    private List<List<String>> partition(List<String> list, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private String buildCsv(List<FinalReportRow> rows, ReportRequest request) {

        BigDecimal total = rows.stream()
                .map(FinalReportRow::getSkuSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        rows.forEach(r -> r.setTotalSpent(total));

        StringWriter writer = new StringWriter();

        try (CSVWriter csv = new CSVWriter(writer)) {

            csv.writeNext(new String[]{
                    "Campaign ID", "Campaign", "SKU", "Product",
                    "Spent", "Total", "Share %", "Period"
            });

            for (FinalReportRow r : rows) {

                String percent = total.compareTo(BigDecimal.ZERO) > 0
                        ? r.getSkuSpent().multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP).toString()
                        : "0";

                csv.writeNext(new String[]{
                        r.getCampaignId(),
                        r.getCampaignTitle(),
                        r.getSku(),
                        r.getProductTitle(),
                        format(r.getSkuSpent()),
                        format(total),
                        percent,
                        r.getPeriod()
                });
            }

        } catch (Exception e) {
            log.error("CSV generation error", e);
            throw new RuntimeException(e);
        }

        return writer.toString();
    }

    private String generateEmptyReport(ReportRequest req) {
        return "Нет данных за период " + req.getStartDate() + " - " + req.getEndDate();
    }

    private String format(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP)
                .toString()
                .replace('.', ',');
    }
}