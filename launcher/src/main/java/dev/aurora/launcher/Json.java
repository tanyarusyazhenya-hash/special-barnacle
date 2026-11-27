package dev.aurora.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny JSON reader/writer (objects are LinkedHashMap, arrays ArrayList, numbers Long or Double). */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		Json j = new Json(text);
		j.ws();
		Object v = j.value();
		j.ws();
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o) {
		return o instanceof Map ? (Map<String, Object>) o : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> arr(Object o) {
		return o instanceof List ? (List<Object>) o : null;
	}

	public static String str(Object o) {
		return o instanceof String s ? s : null;
	}

	public static long num(Object o, long def) {
		return o instanceof Number n ? n.longValue() : def;
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
	}

	private Object value() {
		char c = s.charAt(i);
		if (c == '{') {
			i++;
			Map<String, Object> m = new LinkedHashMap<>();
			ws();
			if (s.charAt(i) == '}') {
				i++;
				return m;
			}
			while (true) {
				ws();
				String k = string();
				ws();
				i++; // ':'
				ws();
				m.put(k, value());
				ws();
				char d = s.charAt(i++);
				if (d == '}') {
					return m;
				}
			}
		}
		if (c == '[') {
			i++;
			List<Object> l = new ArrayList<>();
			ws();
			if (s.charAt(i) == ']') {
				i++;
				return l;
			}
			while (true) {
				ws();
				l.add(value());
				ws();
				char d = s.charAt(i++);
				if (d == ']') {
					return l;
				}
			}
		}
		if (c == '"') {
			return string();
		}
		if (s.startsWith("true", i)) {
			i += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", i)) {
			i += 5;
			return Boolean.FALSE;
		}
		if (s.startsWith("null", i)) {
			i += 4;
			return null;
		}
		int st = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
			i++;
		}
		String n = s.substring(st, i);
		if (n.contains(".") || n.contains("e") || n.contains("E")) {
			return Double.parseDouble(n);
		}
		return Long.parseLong(n);
	}

	private String string() {
		StringBuilder b = new StringBuilder();
		i++; // opening quote
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') {
				return b.toString();
			}
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
					case 'n' -> b.append('\n');
					case 't' -> b.append('\t');
					case 'r' -> b.append('\r');
					case 'b' -> b.append('\b');
					case 'f' -> b.append('\f');
					case 'u' -> {
						b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
						i += 4;
					}
					default -> b.append(e);
				}
			} else {
				b.append(c);
			}
		}
	}

	public static String write(Object o) {
		StringBuilder b = new StringBuilder();
		write(b, o, 0);
		return b.toString();
	}

	private static void write(StringBuilder b, Object o, int depth) {
		String pad = "\t".repeat(depth + 1);
		if (o == null) {
			b.append("null");
		} else if (o instanceof Map<?, ?> m) {
			if (m.isEmpty()) {
				b.append("{}");
				return;
			}
			b.append("{\n");
			int n = 0;
			for (Map.Entry<?, ?> e : m.entrySet()) {
				b.append(pad);
				quote(b, String.valueOf(e.getKey()));
				b.append(": ");
				write(b, e.getValue(), depth + 1);
				b.append(++n < m.size() ? ",\n" : "\n");
			}
			b.append("\t".repeat(depth)).append('}');
		} else if (o instanceof List<?> l) {
			if (l.isEmpty()) {
				b.append("[]");
				return;
			}
			b.append("[\n");
			for (int k = 0; k < l.size(); k++) {
				b.append(pad);
				write(b, l.get(k), depth + 1);
				b.append(k + 1 < l.size() ? ",\n" : "\n");
			}
			b.append("\t".repeat(depth)).append(']');
		} else if (o instanceof String str) {
			quote(b, str);
		} else {
			b.append(o);
		}
	}

	private static void quote(StringBuilder b, String str) {
		b.append('"');
		for (char c : str.toCharArray()) {
			switch (c) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
				}
			}
		}
		b.append('"');
	}
}
