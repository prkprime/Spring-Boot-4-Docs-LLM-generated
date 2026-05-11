package dev.springboot4docs.ch_07_json_jackson;

import java.math.BigDecimal;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class MoneyDeserializer extends ValueDeserializer<Money> {

	@Override
	public Money deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
		String[] parts = parser.getText().split(" ", 2);
		if (parts.length != 2) {
			return context.reportInputMismatch(Money.class, "Money must use '<amount> <currency>' format");
		}
		return new Money(new BigDecimal(parts[0]), parts[1]);
	}

}
