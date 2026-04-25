package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.CampaignProductStatsResponse;
import com.example.sku_statistics_ozon.dto.CreateReportRequest;
import com.example.sku_statistics_ozon.dto.CreateReportResponse;
import com.example.sku_statistics_ozon.dto.ReportDetailResponse;
import com.example.sku_statistics_ozon.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OzonPerformanceClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "https://api-performance.ozon.ru:443";

    public List<String> getActiveCampaignIds(String token, String from, String to) {

        ResponseEntity<CampaignProductStatsResponse> response =
                restTemplate.exchange(
                        buildUrl(from, to),
                        HttpMethod.GET,
                        new HttpEntity<>(createHeaders(token)),
                        CampaignProductStatsResponse.class
                );

        List<CampaignProductStat> rows = Optional.ofNullable(response.getBody())
                .map(CampaignProductStatsResponse::getRows)
                .orElse(List.of());

        log.info("Total products: {}", rows.size());

        return rows.stream()
                .filter(CampaignProductStat::isActive)
                .filter(CampaignProductStat::hasSpent)
                .map(CampaignProductStat::getId)
                .distinct()
                .toList();
    }

    public String createReportWithRetry(String token, List<String> ids,
                                        String from, String to) {

        log.info("id first {}, and size {}", ids.get(0), ids.size());

        for (int i = 0; i < 5; i++) {
            try {
                ResponseEntity<CreateReportResponse> response =
                        restTemplate.exchange(
                                BASE_URL + "/api/client/statistics/json",
                                HttpMethod.POST,
                                new HttpEntity<>(
                                        CreateReportRequest.builder()
                                                .campaigns(ids)
                                                .dateFrom(from)
                                                .dateTo(to)
                                                .build(),
                                        createHeaders(token)
                                ),
                                CreateReportResponse.class
                        );

                log.info("Status {}", response.getStatusCode());
                log.info("Created report {}", response.getBody().toString());

                return Optional.ofNullable(response.getBody())
                        .map(CreateReportResponse::getUuid)
                        .orElseThrow(() -> new RuntimeException("Empty response"));

            } catch (HttpClientErrorException e) {

                String body = e.getResponseBodyAsString();

                log.info("ErrorBody {}", body);

                if (body.contains("Maximum 1")) {
                    sleep(5000);
                    continue;
                }

                throw e;
            }
        }

        throw new RuntimeException("Failed to create report after retry");
    }

    public ReportDetailResponse waitReport(String token, String uuid) {

        int maxAttempts = 15;
        long delay = 10000;

        for (int i = 0; i < maxAttempts; i++) {

            sleep(delay);

            log.info("Polling report UUID={}, attempt {}/{}", uuid, i + 1, maxAttempts);

            try {
                ResponseEntity<ReportDetailResponse> response =
                        restTemplate.exchange(
                                BASE_URL + "/api/client/statistics/report?UUID=" + uuid,
                                HttpMethod.GET,
                                new HttpEntity<>(createHeaders(token)),
                                ReportDetailResponse.class
                        );

                ReportDetailResponse body = response.getBody();

                if (body != null && !body.isEmpty()) {
                    log.info("Report ready: UUID={}, campaigns={}", uuid, body.size());
                    return body;
                }

                log.info("Report not ready yet (empty map)");

                delay += 5000;

            } catch (HttpClientErrorException.NotFound e) {
                log.info("Report not found yet (UUID={})", uuid);
            } catch (HttpClientErrorException e) {
                log.error("Client error: {}", e.getResponseBodyAsString());
                throw e;
            }
        }

        throw new RuntimeException("Отчет не готов после " + maxAttempts + " попыток");
    }

    private String buildUrl(String from, String to) {
        return UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path("/api/client/statistics/campaign/product/json")
                .queryParam("dateFrom", from)
                .queryParam("dateTo", to)
                .toUriString();
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        return headers;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}