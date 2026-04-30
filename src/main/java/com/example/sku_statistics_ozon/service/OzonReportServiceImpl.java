package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import com.example.sku_statistics_ozon.model.CampaignClickStat;
import com.example.sku_statistics_ozon.model.OrderReportItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonReportServiceImpl implements OzonReportService {

    private final OzonPerformanceClient client;

    private static final String STATUS_RUNNING = "running";
    private static final String INSTRUMENT_CPC = "Оплата за клик";
    private static final String INSTRUMENT_CPO = "Оплата за заказ: выбранные товары";
    private static final String SOURCE_CPC = "Кампания за клики";
    private static final String SOURCE_CPO = "Оплата за заказ";

    @Override
    public List<EnrichedCampaignRow> buildReport(
            String dateFrom, String dateTo, String token) {

        String postFrom = dateFrom + "T00:00:00Z";
        String postTo = dateTo + "T00:00:00Z";

        List<CampaignClickStat> allCampaigns = client.getCampaignProductStats(dateFrom, dateTo, token);
        List<OrderReportItem> allOrders = client.getOrderReport(postFrom, postTo, token);

        log.info("Кампаний всего: {}, заказов всего: {}", allCampaigns.size(), allOrders.size());

        List<CampaignClickStat> runningCampaigns = allCampaigns.stream()
                .filter(c -> STATUS_RUNNING.equalsIgnoreCase(c.getStatus()))
                .toList();

        log.info("Running кампаний: {}", runningCampaigns.size());

        Map<String, OrderReportItem> offerIdToMeta = new LinkedHashMap<>();
        Map<String, BigDecimal> offerIdToMoneySpent = new LinkedHashMap<>();

        for (OrderReportItem order : allOrders) {
            String offerId = order.getOfferId();
            if (offerId == null) continue;

            offerIdToMeta.putIfAbsent(offerId, order);

            offerIdToMoneySpent.merge(
                    offerId,
                    nvl(order.getMoneySpent()),
                    BigDecimal::add
            );
        }

        log.info("Уникальных offerId в заказах: {}", offerIdToMeta.size());
        offerIdToMeta.forEach((k, v) ->
                log.debug("offerId={} -> advSku={} moneySpent={}",
                        k, v.getAdvSku(), offerIdToMoneySpent.get(k)));

        List<EnrichedCampaignRow> cpcRows = new ArrayList<>();

        for (CampaignClickStat campaign : runningCampaigns) {
            String detectedOfferId = extractOfferId(campaign.getTitle());

            OrderReportItem meta = offerIdToMeta.get(detectedOfferId);
            BigDecimal ordersMoneySpent = offerIdToMoneySpent.getOrDefault(
                    detectedOfferId, BigDecimal.ZERO);

            String advSku = meta != null ? meta.getAdvSku() : null;
            String title = meta != null ? meta.getTitle() : campaign.getTitle();
            String offerId = detectedOfferId;

            EnrichedCampaignRow row = EnrichedCampaignRow.builder()
                    .campaignId(campaign.getId())
                    .campaignTitle(campaign.getTitle())
                    .objectType(campaign.getObjectType())
                    .status(campaign.getStatus())
                    .placement(campaign.getPlacement())
                    .weeklyBudget(nvl(campaign.getWeeklyBudget()))
                    .moneySpentCpc(nvl(campaign.getMoneySpent()))
                    .views(parseIntSafe(campaign.getViews()))
                    .clicks(parseIntSafe(campaign.getClicks()))
                    .ctr(campaign.getCtr())
                    .clickPrice(campaign.getClickPrice())
                    .ordersCpc(parseIntSafe(campaign.getOrders()))
                    .ordersMoneyСpc(nvl(campaign.getOrdersMoney()))
                    .drr(campaign.getDrr())
                    .toCart(parseIntSafe(campaign.getToCart()))
                    .strategy(campaign.getStrategy())
                    .advSku(advSku)
                    .offerId(offerId)
                    .title(title)
                    .instrument(INSTRUMENT_CPC)
                    .moneySpentOrders(ordersMoneySpent)
                    .build();

            cpcRows.add(row);
        }

        Map<String, List<OrderReportItem>> cpoByOfferId = allOrders.stream()
                .filter(o -> o.getOrdersSource() != null
                        && o.getOrdersSource().contains(SOURCE_CPO)
                        && !o.getOrdersSource().contains(SOURCE_CPC))
                .collect(Collectors.groupingBy(
                        OrderReportItem::getOfferId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        log.info("CPO групп по offerId: {}", cpoByOfferId.size());

        List<EnrichedCampaignRow> cpoRows = new ArrayList<>();

        for (Map.Entry<String, List<OrderReportItem>> entry : cpoByOfferId.entrySet()) {
            String offerId = entry.getKey();
            List<OrderReportItem> orders = entry.getValue();
            OrderReportItem first = orders.get(0);

            BigDecimal totalMoneySpent = offerIdToMoneySpent.get(offerId);

            BigDecimal totalCost = orders.stream()
                    .map(o -> nvl(o.getCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            EnrichedCampaignRow row = EnrichedCampaignRow.builder()
                    .campaignId("-")
                    .campaignTitle("-")
                    .objectType("-")
                    .status("-")
                    .placement("-")
                    .weeklyBudget(BigDecimal.ZERO)
                    .moneySpentCpc(BigDecimal.ZERO)
                    .views(null)
                    .clicks(null)
                    .ctr(null)
                    .clickPrice(null)
                    .ordersCpc(orders.size())
                    .ordersMoneyСpc(totalCost)
                    .drr(calcDrr(totalMoneySpent, totalCost))
                    .toCart(null)
                    .strategy("-")
                    .advSku(first.getAdvSku())
                    .offerId(offerId)
                    .title(first.getTitle())
                    .instrument(INSTRUMENT_CPO)
                    .moneySpentOrders(totalMoneySpent)
                    .build();

            cpoRows.add(row);
        }

        List<EnrichedCampaignRow> result = new ArrayList<>();
        result.addAll(cpcRows);
        result.addAll(cpoRows);

        log.info("Итого строк в отчёте: {} (CPC={}, CPO={})",
                result.size(), cpcRows.size(), cpoRows.size());

        return result;
    }

    private String extractOfferId(String campaignTitle) {
        if (campaignTitle == null || campaignTitle.isBlank()) return null;
        String first = campaignTitle.trim().split("\\s+")[0];
        if (first.matches(".*[\\d-].*")) {
            return first;
        }
        return null;
    }

    private BigDecimal calcDrr(BigDecimal spent, BigDecimal sales) {
        if (spent == null || sales == null
                || sales.compareTo(BigDecimal.ZERO) == 0) return null;
        return spent.divide(sales, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}