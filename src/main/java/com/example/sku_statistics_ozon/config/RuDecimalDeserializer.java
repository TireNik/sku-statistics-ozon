package com.example.sku_statistics_ozon.config;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigDecimal;


public class RuDecimalDeserializer extends StdDeserializer<BigDecimal> {

    public RuDecimalDeserializer() {
        super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctx)
            throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank() || value.equals("-")) return null;
        try {
            return new BigDecimal(value.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
