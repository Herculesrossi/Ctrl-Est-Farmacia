package farmacia.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonMini {

    private JsonMini() {}

    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    public static Object parse(String s) {
        return new Parser(s).parse();
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parse() {
            Object value = parseValue();
            skipWs();
            if (i != s.length()) throw err("Conteudo inesperado");
            return value;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw err("Fim inesperado");
            char ch = s.charAt(i);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expectChar('{');
            skipWs();
            Map<String, Object> out = new LinkedHashMap<>();
            if (peek('}')) { i++; return out; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expectChar(':');
                Object val = parseValue();
                out.put(key, val);
                skipWs();
                if (peek('}')) { i++; return out; }
                expectChar(',');
            }
        }

        private List<Object> parseArray() {
            expectChar('[');
            skipWs();
            List<Object> out = new ArrayList<>();
            if (peek(']')) { i++; return out; }
            while (true) {
                Object v = parseValue();
                out.add(v);
                skipWs();
                if (peek(']')) { i++; return out; }
                expectChar(',');
            }
        }

        private String parseString() {
            expectChar('"');
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char ch = s.charAt(i++);
                if (ch == '"') return out.toString();
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                if (i >= s.length()) throw err("Escape inválido");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw err("Unicode inválido");
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        out.append((char) Integer.parseInt(hex, 16));
                        break;
                    }
                    default: throw err("Escape inválido: \\" + e);
                }
            }
            throw err("String não terminada");
        }

        private Number parseNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean frac = false;
            if (peek('.')) {
                frac = true;
                i++;
                if (i < s.length() && Character.isDigit(s.charAt(i))) {
                    do {
                        i++;
                    } while (i < s.length() && Character.isDigit(s.charAt(i)));
                }
            }
            if (peek('e') || peek('E')) {
                frac = true;
                i++;
                if (peek('+') || peek('-')) i++;
                if (i < s.length() && Character.isDigit(s.charAt(i))) {
                    do {
                        i++;
                    } while (i < s.length() && Character.isDigit(s.charAt(i)));
                }
            }
            String num = s.substring(start, i);
            try {
                if (frac) return Double.parseDouble(num);
                long v = Long.parseLong(num);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return (int) v;
                return v;
            } catch (NumberFormatException e) {
                throw err("Número inválido: " + num);
            }
        }

        private void skipWs() {
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') i++;
                else break;
            }
        }

        private boolean peek(char ch) {
            return i < s.length() && s.charAt(i) == ch;
        }

        private void expectChar(char ch) {
            if (!peek(ch)) throw err("Esperado '" + ch + "'");
            i++;
        }

        private void expect(String lit) {
            if (!s.startsWith(lit, i)) throw err("Esperado " + lit);
            i += lit.length();
        }

        private RuntimeException err(String msg) {
            return new IllegalArgumentException(msg + " (pos " + i + ")");
        }
    }
}
