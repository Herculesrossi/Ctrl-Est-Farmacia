package farmacia.util;

import farmacia.model.Medicamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static farmacia.util.AppConstants.DATE_FMT;

public final class MedicamentoParser {

    private MedicamentoParser() {}

    public static Medicamento fromJsonMap(Map<?, ?> m) {
        String nome = asString(m.get("nome"));
        String lote = asString(m.get("lote"));
        int qtd = asInt(m.get("quantidade"));
        String validadeRaw = asString(m.get("validade"));
        String precoRaw = asString(m.get("preco"));
        String fornecedor = asString(m.get("fornecedor"));

        if (validadeRaw.isBlank()) {
            throw new IllegalArgumentException(required("Validade"));
        }
        if (precoRaw.isBlank()) {
            throw new IllegalArgumentException(required("Pre\u00e7o"));
        }

        LocalDate validade = parseValidade(validadeRaw);
        BigDecimal preco = parsePreco(precoRaw).setScale(2, RoundingMode.HALF_UP);
        return new Medicamento(nome, lote, qtd, validade, preco, fornecedor);
    }

    public static Medicamento fromLegacyCsvParts(String[] parts) {
        String nome = uncsvLegacy(parts[0]);
        String lote = uncsvLegacy(parts[1]);
        int qtd = Integer.parseInt(parts[2]);
        String validadeRaw = parts[3] == null ? "" : parts[3].trim();
        String precoRaw = uncsvLegacy(parts[4]);
        String fornecedor = uncsvLegacy(parts[5]);

        if (validadeRaw.isBlank()) {
            throw new IllegalArgumentException(required("Validade"));
        }
        if (precoRaw.isBlank()) {
            throw new IllegalArgumentException(required("Pre\u00e7o"));
        }

        LocalDate validade = LocalDate.parse(validadeRaw, DATE_FMT);
        BigDecimal preco = parsePreco(precoRaw);
        return new Medicamento(nome, lote, qtd, validade, preco, fornecedor);
    }

    public static LocalDate parseValidade(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new DateTimeParseException("Validade vazia", s, 0);
        if (s.contains("/")) return LocalDate.parse(s, DATE_FMT);
        return LocalDate.parse(s);
    }

    public static BigDecimal parsePreco(String raw) {
        if (raw == null) return BigDecimal.ZERO;
        String s = raw.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;

        s = s.replace(" ", "");
        if (s.startsWith("=\"") && s.endsWith("\"") && s.length() >= 4) {
            s = s.substring(2, s.length() - 1);
        } else if (s.startsWith("=")) {
            s = s.substring(1);
        }
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(',', '.');
        } else if (s.contains(",")) {
            s = s.replace(',', '.');
        }
        BigDecimal preco = new BigDecimal(s);
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new NumberFormatException("Pre\u00e7o n\u00e3o pode ser negativo.");
        }
        return preco;
    }

    public static String[] splitCsvLineLegacy(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char sep = ';';

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == sep && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }

        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        String s = o.toString().trim();
        if (s.isEmpty()) return 0;
        return Integer.parseInt(s);
    }

    private static String uncsvLegacy(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    private static String required(String field) {
        return field + " \u00e9 obrigat\u00f3rio.";
    }
}
