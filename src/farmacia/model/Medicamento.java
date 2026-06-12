package farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Medicamento(
        String nome,
        String lote,
        int quantidade,
        LocalDate validade,
        BigDecimal preco,
        String fornecedor
) {
    public Medicamento {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(required("Nome"));
        }
        if (lote == null || lote.isBlank()) {
            throw new IllegalArgumentException(required("Lote"));
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException(required("Quantidade"));
        }
        if (validade == null) {
            throw new IllegalArgumentException(required("Validade"));
        }
        if (preco == null) {
            throw new IllegalArgumentException(required("Pre\u00e7o"));
        }
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Pre\u00e7o n\u00e3o pode ser negativo.");
        }
        if (fornecedor == null || fornecedor.isBlank()) {
            throw new IllegalArgumentException(required("Fornecedor"));
        }
    }

    private static String required(String field) {
        return field + " \u00e9 obrigat\u00f3rio.";
    }
}
