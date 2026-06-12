package farmacia.repository;

import farmacia.model.Medicamento;
import farmacia.util.JsonMini;

import java.io.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static farmacia.util.AppConstants.DATE_FMT;
import static farmacia.util.AppConstants.jsonPath;
import static farmacia.util.AppConstants.jsonTempPath;
import static farmacia.util.AppConstants.legacyCsvPath;
import static farmacia.util.MedicamentoParser.fromJsonMap;
import static farmacia.util.MedicamentoParser.fromLegacyCsvParts;
import static farmacia.util.MedicamentoParser.splitCsvLineLegacy;

public class EstoqueRepository {

    public record LoadResult(List<Medicamento> medicamentos, boolean legacyFormat, int itensIgnorados) {}

    public Optional<LoadResult> carregar() throws IOException {
        Path path = jsonPath();
        if (!Files.exists(path)) return Optional.empty();

        String json = Files.readString(path, StandardCharsets.UTF_8);
        Object rootObj = JsonMini.parse(json);

        List<?> items;
        boolean legacyArrayFormat = false;
        if (rootObj instanceof Map<?, ?> root) {
            Object itemsObj = root.get("items");
            if (!(itemsObj instanceof List<?> list)) {
                throw new IllegalArgumentException("Campo 'items' inválido");
            }
            items = list;
        } else if (rootObj instanceof List<?> list) {
            items = list;
            legacyArrayFormat = true;
        } else {
            throw new IllegalArgumentException("JSON raiz inválido");
        }

        List<Medicamento> medicamentos = new ArrayList<>();
        int ignorados = 0;
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> m)) {
                ignorados++;
                continue;
            }
            try {
                medicamentos.add(fromJsonMap(m));
            } catch (RuntimeException ex) {
                ignorados++;
                System.err.println("Item ignorado no JSON: " + ex.getMessage());
            }
        }

        if (ignorados > 0) {
            System.err.println("Total de itens ignorados ao carregar: " + ignorados);
        }

        return Optional.of(new LoadResult(medicamentos, legacyArrayFormat, ignorados));
    }

    public void salvar(List<Medicamento> medicamentos) throws IOException {
        Path tmp = jsonTempPath();
        Path out = jsonPath();

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp.toFile()), StandardCharsets.UTF_8))) {

            w.write("{\"version\":1,\"items\":[");

            for (int i = 0; i < medicamentos.size(); i++) {
                Medicamento m = medicamentos.get(i);
                if (i > 0) w.write(',');

                w.write('{');
                w.write("\"nome\":"); w.write(JsonMini.quote(m.nome())); w.write(',');
                w.write("\"lote\":"); w.write(JsonMini.quote(m.lote())); w.write(',');
                w.write("\"quantidade\":"); w.write(Integer.toString(m.quantidade())); w.write(',');
                w.write("\"validade\":"); w.write(JsonMini.quote(m.validade().format(DATE_FMT))); w.write(',');
                w.write("\"preco\":"); w.write(JsonMini.quote(m.preco().setScale(2, RoundingMode.HALF_UP).toPlainString())); w.write(',');
                w.write("\"fornecedor\":"); w.write(JsonMini.quote(m.fornecedor()));
                w.write('}');
            }

            w.write("]}");
            w.newLine();
            w.flush();
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        try {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Optional<List<Medicamento>> migrarCsvLegadoSeNecessario() {
        if (Files.exists(jsonPath())) return Optional.empty();

        Path csv = legacyCsvPath();
        if (!Files.exists(csv)) return Optional.empty();

        List<Medicamento> medicamentos = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv.toFile()), StandardCharsets.UTF_8))) {

            String line = r.readLine();
            if (line == null) return Optional.empty();
            int linhasIgnoradas = 0;

            if (line.trim().toLowerCase().startsWith("sep=")) {
                line = r.readLine();
                if (line == null) return Optional.empty();
            }

            boolean headerLine = line.toLowerCase().contains("nome") && line.contains(";");
            if (!headerLine) {
                if (!adicionarLinhaCsv(medicamentos, line)) linhasIgnoradas++;
            }

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (!adicionarLinhaCsv(medicamentos, line)) linhasIgnoradas++;
            }

            if (linhasIgnoradas > 0) {
                System.err.println("CSV legado mantido para conferencia: " + linhasIgnoradas + " linha(s) ignorada(s).");
                return Optional.of(medicamentos);
            }
        } catch (Exception e) {
            System.err.println("Erro na migração CSV: " + e.getMessage());
            return Optional.empty();
        }

        try {
            Files.deleteIfExists(csv);
        } catch (IOException ex) {
            System.err.println("Não foi possível remover CSV legado: " + ex.getMessage());
        }

        return Optional.of(medicamentos);
    }

    private boolean adicionarLinhaCsv(List<Medicamento> medicamentos, String line) {
        String[] parts = splitCsvLineLegacy(line);
        if (parts.length < 6) {
            System.err.println("Linha CSV ignorada (colunas insuficientes): " + line);
            return false;
        }
        try {
            medicamentos.add(fromLegacyCsvParts(parts));
            return true;
        } catch (RuntimeException ex) {
            System.err.println("Linha CSV ignorada: " + ex.getMessage());
            return false;
        }
    }
}
