package dev.springboot4docs.ch_07_json_jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class MoneySerializer extends ValueSerializer<Money> {

    @Override
    public void serialize(Money value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        generator.writeString(value.amount().toPlainString() + " " + value.currency());
    }

}
