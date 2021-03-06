package com.nhl.link.move.runtime.csv;

import java.io.IOException;
import java.math.BigDecimal;

import com.nhl.link.move.LmRuntimeException;
import com.nhl.link.move.Row;
import com.nhl.link.move.RowAttribute;

/**
 * @since 1.4
 */
class CsvDataRow implements Row {

	private RowAttribute[] attributes;
	private String data;
	private char delimiter;
	private String[] values;

	CsvDataRow(RowAttribute[] attributes, String data, char delimiter) {
		this.attributes = attributes;
		this.data = data;
		this.delimiter = delimiter;
	}

	@Override
	public Object get(RowAttribute attribute) {
		if (values == null) {
			try {
				values = CsvUtils.parse(new String[attributes.length], data, delimiter);
			} catch (IOException e) {
				throw new LmRuntimeException("Failed to parse CSV row", e);
			}
		}
		return getValue(attribute.type(), values[attribute.getOrdinal()]);
	}

	@Override
	public RowAttribute[] attributes() {
		return attributes;
	}

	private static Object getValue(Class<?> type, String value) {

		if (value == null) {
			return null;
		}

		String className = type.getName();
		switch (className) {
		case "java.lang.String": {
			return value;
		}
		case "java.lang.Boolean": {
			if (value.isEmpty()) {
				return null;
			}
			switch (value) {
			case "true":
			case "1":
			case "Y":
			case "T": {
				return true;
			}
			case "false":
			case "0":
			case "N":
			case "F": {
				return false;
			}
			default: {
				throw new LmRuntimeException("Unknown boolean format: " + value);
			}
			}
		}
		case "java.lang.Byte": {
			return value.isEmpty() ? Byte.valueOf((byte) 0) : Byte.valueOf(value);
		}
		case "java.lang.Short": {
			return value.isEmpty() ? Short.valueOf((short) 0) : Short.valueOf(value);
		}
		case "java.lang.Integer": {
			return value.isEmpty() ? Integer.valueOf(0) : Integer.valueOf(value);
		}
		case "java.lang.Long": {
			return value.isEmpty() ? Long.valueOf(0L) : Long.valueOf(value);
		}
		case "java.lang.Float": {
			return value.isEmpty() ? Float.valueOf(0f) : Float.valueOf(value);
		}
		case "java.lang.Double": {
			return value.isEmpty() ? Double.valueOf(0d) : Double.valueOf(value);
		}
		case "java.math.BigDecimal": {
			return value.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(Double.valueOf(value));
		}
		case "java.util.Date":
		case "java.sql.Date":
		case "java.sql.Time":
		case "java.sql.Timestamp": {
			// format ??
			return value.isEmpty() ? null : value;
		}
		default: {
			return value;
		}
		}
	}

}
