package farmacia.util;

import farmacia.model.Medicamento;

import java.time.LocalDate;

public final class ValidadeUtils {

    private ValidadeUtils() {}

    public static boolean isVencido(Medicamento m) {
        return isVencido(m.validade());
    }

    public static boolean isVencido(LocalDate validade) {
        return validade.isBefore(LocalDate.now());
    }

    public static boolean venceEmBreve(Medicamento m, int nDias) {
        return venceEmBreve(m.validade(), nDias);
    }

    public static boolean venceEmBreve(LocalDate validade, int nDias) {
        LocalDate hoje = LocalDate.now();
        boolean vencido = validade.isBefore(hoje);
        return !vencido && !validade.isAfter(hoje.plusDays(nDias));
    }

    public static long contarVencidos(Iterable<Medicamento> medicamentos) {
        LocalDate hoje = LocalDate.now();
        long count = 0;
        for (Medicamento m : medicamentos) {
            if (m.validade().isBefore(hoje)) count++;
        }
        return count;
    }

    public static long contarVencendoEm(Iterable<Medicamento> medicamentos, int nDias) {
        long count = 0;
        for (Medicamento m : medicamentos) {
            if (venceEmBreve(m, nDias)) count++;
        }
        return count;
    }
}
