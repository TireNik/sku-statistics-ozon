package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.CampaignReportWrapper;
import com.example.sku_statistics_ozon.dto.DateRangeRequest;
import com.example.sku_statistics_ozon.dto.OzonRowsResponse;
import com.example.sku_statistics_ozon.model.CampaignClickStat;
import com.example.sku_statistics_ozon.model.CampaignExpenseStat;
import com.example.sku_statistics_ozon.model.OrderReportItem;
import com.example.sku_statistics_ozon.model.ProductReportItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, CampaignReportWrapper> getCampaignReport(
            List<String> campaignIds,
            String dateFrom,
            String dateTo,
            String token
    ) {

        Map<String, CampaignReportWrapper> result = new HashMap<>();

        List<List<String>> batches = partition(campaignIds, 10);

        log.info("Начато формирование отчётов. Батчей: {}", batches.size());

        for (int i = 0; i < batches.size(); i++) {

            List<String> batch = batches.get(i);

            log.info("Отправка батча {}/{} (campaigns={})",
                    i + 1, batches.size(), batch.size());

            String uuid = requestCampaignReport(batch, dateFrom, dateTo, token);

            log.info("Получен UUID={} для батча {}/{}", uuid, i + 1, batches.size());

            String raw = pollUntilReady(uuid, token);

            try {
                Map<String, CampaignReportWrapper> map =
                        objectMapper.readValue(raw, new TypeReference<>() {});
                result.putAll(map);

            } catch (Exception e) {
                throw new RuntimeException(
                        "Ошибка парсинга campaign report UUID=" + uuid, e);
            }
        }

        log.info("Все отчёты сформированы. Всего кампаний обработано: {}", result.size());

        return result;
    }

    private String requestCampaignReport(
            List<String> campaigns,
            String from,
            String to,
            String token
    ) {

        Map<String, Object> body = Map.of(
                "campaigns", campaigns,
                "dateFrom", from,
                "dateTo", to
        );

        String raw = ozonRestClient.post()
                .uri("/api/client/statistics/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(raw);

            String uuid = node.path("UUID").asText(null);

            if (uuid == null || uuid.isBlank()) {
                throw new RuntimeException("UUID не получен. Ответ: " + raw);
            }

            return uuid;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга UUID campaign report", e);
        }
    }

    private List<List<String>> partition(List<String> list, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
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

                if (node.isObject() && node.size() > 0 && !node.has("error")) {
                    log.info("Отчёт готов (map campaignId), UUID={}", uuid);
                    return raw;
                }

                if (node.has("error")) {
                    String error = node.get("error").asText();
                    if ("report not found".equalsIgnoreCase(error)) {
                        log.info("Отчёт ещё не готов (report not found), ждём {}с...", POLL_INTERVAL_SEC);
                        sleep(POLL_INTERVAL_SEC);
                        continue;
                    }
                    throw new RuntimeException("Ошибка отчёта: " + raw);
                }

                log.info("Отчёт формируется (нет данных), ждём {}с...", POLL_INTERVAL_SEC);
                sleep(POLL_INTERVAL_SEC);

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