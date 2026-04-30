package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class CsvExportServiceImpl implements CsvExportService {

    private static final String[] HEADERS = {
            "SKU", "Название товара", "Инструмент", "Место размещения",
            "ID кампании", "Расход ₽", "ДРР %", "Продажи ₽", "Заказы шт",
            "CTR %", "Показы", "Клики", "В корзину", "Конверсия в корзину %",
            "Затраты на заказ ₽", "Стоимость клика ₽"
    };

    public byte[] exportToCsv(List<EnrichedCampaignRow> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);

            writer.println(String.join("\t", HEADERS));

            for (EnrichedCampaignRow row : rows) {
                BigDecimal effectiveSpent = row.getCtr() == null
                        ? row.getMoneySpentOrders()
                        : row.getMoneySpentCpc();

                Integer ordersCpc = row.getOrdersCpc();
                Integer clicks = row.getClicks();
                Integer toCart = row.getToCart();

                BigDecimal toCartConversion = clicks != null && clicks > 0 && toCart != null
                        ? BigDecimal.valueOf(toCart)
                        .divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        : null;

                // Стоимость заказа: effectiveSpent / ordersCpc
                BigDecimal costPerOrder = ordersCpc != null && ordersCpc > 0
                        ? effectiveSpent != null ? effectiveSpent.divide(BigDecimal.valueOf(ordersCpc), 2, RoundingMode.HALF_UP) : null
                        : null;

                writer.println(String.join("\t",
                        safe(row.getAdvSku()),
                        safe(row.getTitle()),
                        safe(row.getInstrument()),
                        safe(row.getPlacement()),
                        safe(row.getCampaignId()),
                        formatMoney(effectiveSpent), //расходы
                        formatPercent(row.getDrr()),
                        formatMoney(row.getOrdersMoneyСpc()),
                        formatInt(ordersCpc), //заказа
                        formatPercent(row.getCtr()),
                        formatInt(row.getViews()),
                        formatInt(clicks), //кликов
                        formatInt(toCart), //в корзине
                        formatPercent(toCartConversion), // в корзину / на клики * 100
                        formatMoney(costPerOrder), //расходы / заказы в штуках
                        formatMoney(row.getClickPrice())
                ));
            }

            writer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }

    private String safe(String v) {
        return v != null ? v : "-";
    }

    private String formatMoney(BigDecimal v) {
        if (v == null) return "-";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }

    private String formatPercent(BigDecimal v) {
        if (v == null) return "-";
        return v.toPlainString().replace(".", ",") + "%";
    }

    private String formatInt(Integer v) { return v != null ? String.valueOf(v) : "-"; }
}