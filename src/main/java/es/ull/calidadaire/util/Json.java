package es.ull.calidadaire.util;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lector/escritor JSON mínimo sin dependencias externas. */
public final class Json {
    private Json() {}

    // ---------- Lectura ----------
    public static Object parse(String text) { return new Parser(text).document(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Se esperaba un objeto JSON");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) {
        if (o instanceof List<?> l) return (List<Object>) l;
        throw new IllegalArgumentException("Se esperaba un array JSON");
    }

    public static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static long longValue(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.longValue() : 0L;
    }

    public static int intValue(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.intValue() : 0;
    }

    public static double doubleValue(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** Número que puede faltar: devuelve null si no existe. */
    public static Double doubleOrNull(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.doubleValue() : null;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        return m.get(key) instanceof Boolean b && b;
    }

    /** Fecha ISO (yyyy-MM-dd); acepta también fecha-hora y toma la parte de fecha. */
    public static LocalDate date(Map<String, Object> m, String key) {
        String s = string(m, key);
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    }

    // ---------- Escritura ----------
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) { sb.append("null"); }
        else if (v instanceof String || v instanceof LocalDate || v instanceof Enum<?>) { quote(v.toString(), sb); }
        else if (v instanceof Double d && (d.isNaN() || d.isInfinite())) { sb.append("null"); }
        else if (v instanceof Number || v instanceof Boolean) { sb.append(v); }
        else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                write(o, sb);
            }
            sb.append(']');
        } else if (v instanceof Record r) {
            Map<String, Object> map = new LinkedHashMap<>();
            try {
                for (RecordComponent rc : r.getClass().getRecordComponents()) {
                    var acc = rc.getAccessor();
                    acc.setAccessible(true);
                    map.put(rc.getName(), acc.invoke(r));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            write(map, sb);
        } else {
            quote(v.toString(), sb);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------- Parser ----------
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        Object document() {
            ws();
            Object v = value();
            ws();
            if (i < s.length()) throw err("Contenido sobrante");
            return v;
        }

        private Object value() {
            if (i >= s.length()) throw err("Fin inesperado");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> obj();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> { lit("true"); yield Boolean.TRUE; }
                case 'f' -> { lit("false"); yield Boolean.FALSE; }
                case 'n' -> { lit("null"); yield null; }
                default -> num();
            };
        }

        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                if (peek() != '"') throw err("Se esperaba una clave");
                String k = str();
                ws();
                if (peek() != ':') throw err("Se esperaba ':'");
                i++; ws();
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw err("Se esperaba ',' o '}'");
            }
        }

        private List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw err("Se esperaba ',' o ']'");
            }
        }

        private String str() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                if (i >= s.length()) throw err("Cadena sin cerrar");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e);
                }
            }
        }

        private Number num() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw err("Valor no válido");
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(t);
            }
        }

        private void lit(String w) {
            if (!s.startsWith(w, i)) throw err("Literal no válido");
            i += w.length();
        }

        private char peek() {
            if (i >= s.length()) throw err("Fin inesperado");
            return s.charAt(i);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private IllegalArgumentException err(String m) {
            return new IllegalArgumentException("JSON inválido en posición " + i + ": " + m);
        }
    }
}
