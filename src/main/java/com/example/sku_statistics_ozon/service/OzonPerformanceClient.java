package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.DateRangeRequest;
import com.example.sku_statistics_ozon.dto.OzonRowsResponse;
import com.example.sku_statistics_ozon.model.CampaignClickStat;
import com.example.sku_statistics_ozon.model.CampaignExpenseStat;
import com.example.sku_statistics_ozon.model.OrderReportItem;
import com.example.sku_statistics_ozon.model.ProductReportItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonPerformanceClient {

    private final RestClient ozonRestClient;
    private final ObjectMapper objectMapper;

    private static final int    POLL_INTERVAL_SEC   = 10;
    private static final int    POLL_MAX_ATTEMPTS   = 30;

    public List<CampaignClickStat> getCampaignProductStats(
            String dateFrom, String dateTo, String token) {
        log.info("Запрос статистики кампаний: {} - {}", dateFrom, dateTo);
        OzonRowsResponse<CampaignClickStat> response = ozonRestClient.get()
                .uri(u -> u.path("/api/client/statistics/campaign/product/json")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.getRows() : List.of();
    }

    public List<CampaignExpenseStat> getDailyStats(
            String dateFrom, String dateTo, String token) {
        log.info("Запрос дневной статистики: {} - {}", dateFrom, dateTo);
        OzonRowsResponse<CampaignExpenseStat> response = ozonRestClient.get()
                .uri(u -> u.path("/api/client/statistics/daily/json")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.getRows() : List.of();
    }

    public List<OrderReportItem> getOrderReport(
            String from, String to, String token) {
        log.info("Запрос отчёта по заказам: {} - {}", from, to);
        String uuid = requestReportGeneration(
                "/api/client/statistic/orders/generate/json", from, to, token);
        String rawJson = pollUntilReady(uuid, token);
        return parseRows(rawJson, OrderReportItem.class);
    }

    public List<ProductReportItem> getProductReport(
            String from, String to, String token) {
        log.info("Запрос отчёта по товарам: {} - {}", from, to);
        String uuid = requestReportGeneration(
                "/api/client/statistic/products/generate/json", from, to, token);
        String rawJson = pollUntilReady(uuid, token);
        return parseRows(rawJson, ProductReportItem.class);
    }

    private String requestReportGeneration(
            String uri, String from, String to, String token) {
        String raw = ozonRestClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new DateRangeRequest(from, to))
                .retrieve()
                .body(String.class);

        log.info("Ответ генерации отчёта [{}]: {}", uri, raw);

        try {
            JsonNode node = objectMapper.readTree(raw);
            String uuid = node.path("UUID").asText(null);
            if (uuid == null || uuid.isBlank()) {
                throw new RuntimeException("UUID не получен. Ответ: " + raw);
            }
            log.info("Получен UUID: {}", uuid);
            return uuid;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга UUID: " + e.getMessage(), e);
        }
    }

    private String pollUntilReady(String uuid, String token) {
        log.info("Ожидание начала формирования отчёта UUID={} (5с)...", uuid);
        sleep(5);

        for (int attempt = 1; attempt <= POLL_MAX_ATTEMPTS; attempt++) {
            log.info("Проверка готовности UUID={}, попытка {}/{}", uuid, attempt, POLL_MAX_ATTEMPTS);

            String raw;
            try {
                raw = ozonRestClient.get()
                        .uri(u -> u.path("/api/client/statistics/report")
                                .queryParam("UUID", uuid)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(String.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404) {
                    log.info("Отчёт ещё не найден (404), ждём {}с... UUID={}",
                            POLL_INTERVAL_SEC, uuid);
                    sleep(POLL_INTERVAL_SEC);
                    continue;
                }
                throw new RuntimeException("Ошибка запроса отчёта: " + e.getMessage(), e);
            }

            log.info("Ответ поллинга UUID={}: {}", uuid,
                    raw != null ? raw.substring(0, Math.min(500, raw.length())) : "NULL");

            if (raw == null || raw.isBlank()) {
                sleep(POLL_INTERVAL_SEC);
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(raw);

                if (node.has("rows")) {
                    log.info("Отчёт готов (есть rows), UUID={}", uuid);
                    return raw;
                }

                String state = node.path("state").asText(
                        node.path("status").asText(""));

                log.info("Статус отчёта: '{}', UUID={}", state, uuid);

                switch (state.toUpperCase()) {
                    case "OK", "DONE", "COMPLETED", "SUCCESS", "FINISH" -> {
                        log.info("Отчёт готов по статусу, UUID={}", uuid);
                        return raw;
                    }
                    case "ERROR", "FAILED" -> throw new RuntimeException(
                            "Ошибка генерации отчёта UUID=" + uuid +
                                    ", ответ: " + raw);
                    default -> {
                        log.info("Отчёт формируется (state='{}'), ждём {}с...",
                                state, POLL_INTERVAL_SEC);
                        sleep(POLL_INTERVAL_SEC);
                    }
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Ошибка парсинга ответа UUID=" + uuid, e);
            }
        }

        throw new RuntimeException(
                "Таймаут ожидания отчёта (" + (POLL_MAX_ATTEMPTS * POLL_INTERVAL_SEC) +
                        "с). UUID=" + uuid);
    }

    private <T> List<T> parseRows(String raw, Class<T> itemClass) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode rowsNode = root.path("rows");

            if (!rowsNode.isMissingNode() && rowsNode.isArray()) {
                List<T> result = objectMapper.convertValue(
                        rowsNode,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, itemClass));
                log.info("Распарсено записей [{}]: {}", itemClass.getSimpleName(), result.size());
                return result;
            }

            if (root.isArray()) {
                List<T> result = objectMapper.convertValue(
                        root,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, itemClass));
                log.info("Распарсено записей (массив) [{}]: {}", itemClass.getSimpleName(), result.size());
                return result;
            }

            log.warn("Неожиданная структура ответа: {}", raw.substring(0, Math.min(200, raw.length())));
            return List.of();

        } catch (Exception e) {
            log.error("Ошибка парсинга ответа [{}]: {}", itemClass.getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Прерывание во время ожидания отчёта", e);
        }
    }

}