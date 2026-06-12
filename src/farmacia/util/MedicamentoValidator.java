package farmacia.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static farmacia.util.AppConstants.MAX_FORNECEDOR_LENGTH;
import static farmacia.util.AppConstants.MAX_LOTE_LENGTH;
import static farmacia.util.AppConstants.MAX_NOME_LENGTH;
import static farmacia.util.MedicamentoParser.parsePreco;
import static farmacia.util.MedicamentoParser.parseValidade;

public final class MedicamentoValidator {

    public record Result(boolean valid, String message, LocalDate validade, BigDecimal preco) {
        public static Result ok(LocalDate validade, BigDecimal preco) {
            return new Result(true, "", validade, preco);
        }

        public static Result error(String message) {
            return new Result(false, message, null, null);
        }
    }

    private MedicamentoValidator() {}

    public static Result validate(String nome, String lote, int quantidade, String validadeStr, String precoStr, String fornecedor) {
        if (nome == null || nome.isBlank()) {
            return Result.error(required("Nome"));
        }
        if (nome.length() > MAX_NOME_LENGTH) {
            return Result.error("Nome deve ter no m\u00e1ximo " + MAX_NOME_LENGTH + " caracteres.");
        }
        if (lote == null || lote.isBlank()) {
            return Result.error(required("Lote"));
        }
        if (lote.length() > MAX_LOTE_LENGTH) {
            return Result.error("Lote deve ter no m\u00e1ximo " + MAX_LOTE_LENGTH + " caracteres.");
        }
        if (quantidade <= 0) {
            return Result.error(required("Quantidade"));
        }
        if (validadeStr == null || validadeStr.isBlank()) {
            return Result.error(required("Validade"));
        }
        if (precoStr == null || precoStr.isBlank()) {
            return Result.error(required("Pre\u00e7o"));
        }
        if (fornecedor == null || fornecedor.isBlank()) {
            return Result.error(required("Fornecedor"));
        }
        if (fornecedor.length() > MAX_FORNECEDOR_LENGTH) {
            return Result.error("Fornecedor deve ter no m\u00e1ximo " + MAX_FORNECEDOR_LENGTH + " caracteres.");
        }

        LocalDate validade;
        try {
            validade = parseValidade(validadeStr);
        } catch (DateTimeParseException ex) {
            return Result.error("Data inv\u00e1lida. Use dd/MM/yyyy.");
        }

        BigDecimal preco;
        try {
            preco = parsePreco(precoStr);
        } catch (NumberFormatException ex) {
            return Result.error("Pre\u00e7o inv\u00e1lido. Ex: 12,50 ou 1.234,56");
        }
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("Pre\u00e7o n\u00e3o pode ser negativo.");
        }

        return Result.ok(validade, preco);
    }

    private static String required(String field) {
        return field + " \u00e9 obrigat\u00f3rio.";
    }

    public static String formatPreco(BigDecimal preco) {
        if (preco == null) return "0,00";
        return preco.setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
    }
}
