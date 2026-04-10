import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FarmaciaApp extends JFrame {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DEFAULT_JSON = "estoque_farmacia.json";
    private static final String LEGACY_CSV = "estoque_farmacia.csv";

    private final JTextField nomeField = new JTextField(16);
    private final JTextField loteField = new JTextField(10);
    private final JSpinner qtdSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1_000_000, 1));
    private final JTextField validadeField = new JTextField(10);
    private final JTextField precoField = new JTextField(8);
    private final JTextField fornecedorField = new JTextField(14);

    private final JButton addBtn = new JButton("Cadastrar");
    private final JButton editBtn = new JButton("Editar selecionado");
    private final JButton removeBtn = new JButton("Remover selecionado");
    private final JButton saveBtn = new JButton("Salvar");
    private final JButton loadBtn = new JButton("Carregar");

    private final JRadioButton rbTodos = new JRadioButton("Todos", true);
    private final JRadioButton rbVencidos = new JRadioButton("Só vencidos");
    private final JRadioButton rbAteNDias = new JRadioButton("Vence em até N dias");
    private final JSpinner diasSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 3650, 1));

    private final JLabel infoLabel = new JLabel(" ");

    private final EstoqueTableModel tableModel = new EstoqueTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<EstoqueTableModel> sorter = new TableRowSorter<>(tableModel);

    public FarmaciaApp() {
        super("Controle de Estoque - Farmácia (Desktop)");

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100, 540);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        root.add(buildFormPanel(), BorderLayout.NORTH);

        configureTable();
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        root.add(buildBottomPanel(), BorderLayout.SOUTH);

        Masks.installDateMask(validadeField);
        Masks.installMoneyMask(precoField);

        addBtn.addActionListener(e -> cadastrar());
        editBtn.addActionListener(e -> editarSelecionado());
        removeBtn.addActionListener(e -> removerSelecionado());
        saveBtn.addActionListener(e -> salvarJSONAtomico(true));
        loadBtn.addActionListener(e -> carregarJSON(true));

        rbTodos.addActionListener(e -> aplicarFiltro());
        rbVencidos.addActionListener(e -> aplicarFiltro());
        rbAteNDias.addActionListener(e -> aplicarFiltro());
        diasSpinner.addChangeListener(e -> {
            if (rbAteNDias.isSelected()) aplicarFiltro();
            atualizarContadores();
            table.repaint();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salvarJSONAtomico(false);
                dispose();
                System.exit(0);
            }
        });

        migrateLegacyCsvToJsonIfNeeded();
        carregarJSON(false);
        aplicarFiltro();
        atualizarContadores();
    }

    private JPanel buildFormPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Cadastro de medicamento"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        int y = 0;

        c.gridx = 0; c.gridy = y; p.add(new JLabel("Nome:"), c);
        c.gridx = 1; c.gridy = y; p.add(nomeField, c);

        c.gridx = 2; c.gridy = y; p.add(new JLabel("Lote:"), c);
        c.gridx = 3; c.gridy = y; p.add(loteField, c);

        c.gridx = 4; c.gridy = y; p.add(new JLabel("Qtd:"), c);
        c.gridx = 5; c.gridy = y; p.add(qtdSpinner, c);

        y++;
        c.gridx = 0; c.gridy = y; p.add(new JLabel("Validade (dd/MM/yyyy):"), c);
        c.gridx = 1; c.gridy = y; p.add(validadeField, c);

        c.gridx = 2; c.gridy = y; p.add(new JLabel("Preço (ex: 12,50):"), c);
        c.gridx = 3; c.gridy = y; p.add(precoField, c);

        c.gridx = 4; c.gridy = y; p.add(new JLabel("Fornecedor:"), c);
        c.gridx = 5; c.gridy = y; p.add(fornecedorField, c);

        y++;
        c.gridx = 0; c.gridy = y;
        c.gridwidth = 6;
        c.fill = GridBagConstraints.HORIZONTAL;

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.add(addBtn);
        btnRow.add(editBtn);
        btnRow.add(removeBtn);
        btnRow.add(saveBtn);
        btnRow.add(loadBtn);

        p.add(btnRow, c);

        return p;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros"));

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbTodos);
        bg.add(rbVencidos);
        bg.add(rbAteNDias);

        filtros.add(rbTodos);
        filtros.add(rbVencidos);
        filtros.add(rbAteNDias);
        filtros.add(new JLabel("N ="));
        filtros.add(diasSpinner);
        filtros.add(new JLabel("dias"));

        p.add(filtros, BorderLayout.WEST);

        infoLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
        p.add(infoLabel, BorderLayout.CENTER);

        return p;
    }

    private void configureTable() {
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);

        DefaultTableCellRenderer base = (DefaultTableCellRenderer) table.getDefaultRenderer(Object.class);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

                Component c = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                int modelRow = table.convertRowIndexToModel(row);
                Medicamento m = tableModel.getAt(modelRow);

                LocalDate hoje = LocalDate.now();
                int nDias = (Integer) diasSpinner.getValue();

                boolean vencido = m.validade().isBefore(hoje);
                boolean vencendoEmBreve = !vencido && !m.validade().isAfter(hoje.plusDays(nDias));

                if (isSelected) {
                    c.setForeground(Color.WHITE);
                } else if (vencido) {
                    c.setForeground(Color.RED);
                } else if (vencendoEmBreve) {
                    c.setForeground(new Color(205, 102, 0));
                } else {
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });

        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof LocalDate d) {
                    setText(d.format(FMT));
                } else {
                    setText(value == null ? "" : value.toString());
                }
            }
        });

        sorter.setComparator(3, (a, b) -> {
            LocalDate da = (LocalDate) a;
            LocalDate db = (LocalDate) b;
            return da.compareTo(db);
        });
    }

    private void cadastrar() {
        String nome = nomeField.getText().trim();
        String lote = loteField.getText().trim();
        int qtd = (Integer) qtdSpinner.getValue();
        String validadeStr = validadeField.getText().trim();
        String precoStr = precoField.getText().trim();
        String fornecedor = fornecedorField.getText().trim();

        if (nome.isEmpty() || validadeStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nome e validade são obrigatórios.");
            return;
        }

        LocalDate validade;
        try {
            validade = LocalDate.parse(validadeStr, FMT);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Data inválida. Use dd/MM/yyyy.");
            return;
        }

        BigDecimal preco = BigDecimal.ZERO;
        if (!precoStr.isEmpty()) {
            try {
                preco = parsePreco(precoStr);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Preço inválido. Ex: 12,50 ou 1.234,56");
                return;
            }
        }

        Medicamento m = new Medicamento(nome, lote, qtd, validade, preco, fornecedor);
        tableModel.add(m);

        limparFormulario();
        aplicarFiltro();
        atualizarContadores();
        table.repaint();

        salvarJSONAtomico(false);
    }

    private void editarSelecionado() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha para editar.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        Medicamento atual = tableModel.getAt(modelRow);

        JTextField nome = new JTextField(atual.nome());
        JTextField lote = new JTextField(atual.lote());
        JSpinner qtd = new JSpinner(new SpinnerNumberModel(atual.quantidade(), 0, 1_000_000, 1));
        JTextField validade = new JTextField(atual.validade().format(FMT));
        JTextField preco = new JTextField(atual.preco().toPlainString().replace('.', ','));
        JTextField fornecedor = new JTextField(atual.fornecedor());

        Masks.installDateMask(validade);
        Masks.installMoneyMask(preco);
        preco.setText(preco.getText());

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Nome:")); panel.add(nome);
        panel.add(new JLabel("Lote:")); panel.add(lote);
        panel.add(new JLabel("Quantidade:")); panel.add(qtd);
        panel.add(new JLabel("Validade (dd/MM/yyyy):")); panel.add(validade);
        panel.add(new JLabel("Preço:")); panel.add(preco);
        panel.add(new JLabel("Fornecedor:")); panel.add(fornecedor);

        int res = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Editar medicamento",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (res != JOptionPane.OK_OPTION) return;

        String nomeStr = nome.getText().trim();
        String loteStr = lote.getText().trim();
        int qtdInt = (Integer) qtd.getValue();
        String validadeStr = validade.getText().trim();
        String precoStr = preco.getText().trim();
        String fornecedorStr = fornecedor.getText().trim();

        if (nomeStr.isEmpty() || validadeStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nome e validade são obrigatórios.");
            return;
        }

        LocalDate validadeDate;
        try {
            validadeDate = LocalDate.parse(validadeStr, FMT);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Data inválida. Use dd/MM/yyyy.");
            return;
        }

        BigDecimal precoValue = BigDecimal.ZERO;
        if (!precoStr.isEmpty()) {
            try {
                precoValue = parsePreco(precoStr);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Preço inválido. Ex: 12,50 ou 1.234,56");
                return;
            }
        }

        Medicamento novo = new Medicamento(
                nomeStr, loteStr, qtdInt, validadeDate, precoValue, fornecedorStr
        );

        tableModel.updateAt(modelRow, novo);

        aplicarFiltro();
        atualizarContadores();
        table.repaint();

        salvarJSONAtomico(false);
    }

    private void removerSelecionado() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha para remover.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remover o item selecionado?",
                "Confirmar remoção",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        int modelRow = table.convertRowIndexToModel(viewRow);
        tableModel.removeAt(modelRow);

        aplicarFiltro();
        atualizarContadores();
        table.repaint();

        salvarJSONAtomico(false);
    }

    private void aplicarFiltro() {
        if (rbTodos.isSelected()) {
            sorter.setRowFilter(null);
            atualizarContadores();
            return;
        }

        LocalDate hoje = LocalDate.now();
        int nDias = (Integer) diasSpinner.getValue();

        if (rbVencidos.isSelected()) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends EstoqueTableModel, ? extends Integer> entry) {
                    Medicamento m = tableModel.getAt(entry.getIdentifier());
                    return m.validade().isBefore(hoje);
                }
            });
        } else if (rbAteNDias.isSelected()) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends EstoqueTableModel, ? extends Integer> entry) {
                    Medicamento m = tableModel.getAt(entry.getIdentifier());
                    boolean vencido = m.validade().isBefore(hoje);
                    boolean ateN = !m.validade().isAfter(hoje.plusDays(nDias));
                    return !vencido && ateN;
                }
            });
        }

        atualizarContadores();
    }

    private void atualizarContadores() {
        LocalDate hoje = LocalDate.now();
        int nDias = (Integer) diasSpinner.getValue();

        int total = tableModel.getRowCount();
        long vencidos = tableModel.data.stream().filter(m -> m.validade().isBefore(hoje)).count();
        long ateN = tableModel.data.stream().filter(m -> {
            boolean vencido = m.validade().isBefore(hoje);
            boolean prox = !m.validade().isAfter(hoje.plusDays(nDias));
            return !vencido && prox;
        }).count();

        infoLabel.setText(String.format(
                "Total: %d | Vencidos: %d | Vencendo em até %d dias: %d",
                total, vencidos, nDias, ateN
        ));
    }

    private void limparFormulario() {
        nomeField.setText("");
        loteField.setText("");
        qtdSpinner.setValue(1);
        validadeField.setText("");
        precoField.setText("");
        fornecedorField.setText("");
    }

    private void migrateLegacyCsvToJsonIfNeeded() {
        File json = new File(DEFAULT_JSON);
        if (json.exists()) return;

        File csv = new File(LEGACY_CSV);
        if (!csv.exists()) return;

        List<Medicamento> novos = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {

            String line = r.readLine();
            if (line == null) return;

            if (line.trim().toLowerCase().startsWith("sep=")) {
                line = r.readLine();
                if (line == null) return;
            }

            // Skip header: if this line is not the header, treat as first data row
            boolean headerLine = line.toLowerCase().contains("nome") && line.contains(";");
            if (!headerLine) {
                String[] parts = splitCsvLineLegacy(line);
                if (parts.length >= 6) {
                    novos.add(parseMedicamentoFromLegacyCsv(parts));
                }
            }

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = splitCsvLineLegacy(line);
                if (parts.length < 6) continue;
                novos.add(parseMedicamentoFromLegacyCsv(parts));
            }
        } catch (Exception e) {
            return;
        }

        tableModel.setAll(novos);
        salvarJSONAtomico(false);

        // Remove legacy CSV after successful migration
        try {
            Files.deleteIfExists(csv.toPath());
        } catch (IOException ignored) {
            // If deletion fails, we still proceed with JSON as source of truth.
        }
    }

    private static Medicamento parseMedicamentoFromLegacyCsv(String[] parts) {
        String nome = uncsvLegacy(parts[0]);
        String lote = uncsvLegacy(parts[1]);
        int qtd = Integer.parseInt(parts[2]);
        LocalDate validade = LocalDate.parse(parts[3], FMT);
        BigDecimal preco = parsePreco(uncsvLegacy(parts[4]));
        String fornecedor = uncsvLegacy(parts[5]);
        return new Medicamento(nome, lote, qtd, validade, preco, fornecedor);
    }

    private void salvarJSONAtomico(boolean mostrarMensagem) {
        File tmp = new File(DEFAULT_JSON + ".tmp");
        File out = new File(DEFAULT_JSON);

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {

            w.write("{\"version\":1,\"items\":[");

            for (int i = 0; i < tableModel.data.size(); i++) {
                Medicamento m = tableModel.data.get(i);
                if (i > 0) w.write(',');

                w.write('{');
                w.write("\"nome\":"); w.write(JsonMini.quote(m.nome())); w.write(',');
                w.write("\"lote\":"); w.write(JsonMini.quote(m.lote())); w.write(',');
                w.write("\"quantidade\":"); w.write(Integer.toString(m.quantidade())); w.write(',');
                w.write("\"validade\":"); w.write(JsonMini.quote(m.validade().format(FMT))); w.write(',');
                w.write("\"preco\":"); w.write(JsonMini.quote(m.preco().setScale(2, RoundingMode.HALF_UP).toPlainString())); w.write(',');
                w.write("\"fornecedor\":"); w.write(JsonMini.quote(m.fornecedor()));
                w.write('}');
            }

            w.write("]}");
            w.newLine();
            w.flush();
        } catch (IOException e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar JSON: " + e.getMessage());
            }
            if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
            return;
        }

        try {
            try {
                Files.move(tmp.toPath(), out.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp.toPath(), out.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Salvo em: " + DEFAULT_JSON);
            }
        } catch (IOException e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro final ao salvar JSON: " + e.getMessage());
            }
        }
    }

    private void carregarJSON(boolean mostrarMensagem) {
        File f = new File(DEFAULT_JSON);
        if (!f.exists()) return;

        try {
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Object rootObj = JsonMini.parse(json);
            if (!(rootObj instanceof Map<?, ?> root)) throw new IllegalArgumentException("JSON raiz inválido");

            Object itemsObj = root.get("items");
            if (!(itemsObj instanceof List<?> items)) throw new IllegalArgumentException("Campo 'items' inválido");

            List<Medicamento> novos = new ArrayList<>();
            for (Object it : items) {
                if (!(it instanceof Map<?, ?> m)) continue;

                String nome = asString(m.get("nome"));
                String lote = asString(m.get("lote"));
                int qtd = asInt(m.get("quantidade"));
                LocalDate validade = LocalDate.parse(asString(m.get("validade")), FMT);
                BigDecimal preco = new BigDecimal(asString(m.get("preco"))).setScale(2, RoundingMode.HALF_UP);
                String fornecedor = asString(m.get("fornecedor"));

                novos.add(new Medicamento(nome, lote, qtd, validade, preco, fornecedor));
            }

            tableModel.setAll(novos);
            aplicarFiltro();
            atualizarContadores();
            table.repaint();

            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Carregado de: " + DEFAULT_JSON);
            }
        } catch (Exception e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar JSON: " + e.getMessage());
            }
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        return Integer.parseInt(o.toString());
    }

    // Legacy CSV parsing (one-time migration only)
    private static String uncsvLegacy(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    private static String[] splitCsvLineLegacy(String line) {
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

    private static BigDecimal parsePreco(String raw) {
        if (raw == null) return BigDecimal.ZERO;
        String s = raw.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;

        // Accept "1.234,56" (pt-BR) and "1234.56" (en-US) and also plain digits.
        s = s.replace(" ", "");
        // Excel formula style: ="1.234,56"
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
        return new BigDecimal(s);
    }

    // CSV formatting helpers removed (project is JSON-only now)

    static final class JsonMini {
        private JsonMini() {}

        static String quote(String s) {
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

        static Object parse(String s) {
            return new Parser(s).parseValue();
        }

        private static final class Parser {
            private final String s;
            private int i = 0;

            Parser(String s) {
                this.s = s == null ? "" : s;
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

    record Medicamento(
            String nome,
            String lote,
            int quantidade,
            LocalDate validade,
            BigDecimal preco,
            String fornecedor
    ) {
        Medicamento {
            lote = lote == null ? "" : lote;
            preco = preco == null ? BigDecimal.ZERO : preco;
            fornecedor = fornecedor == null ? "" : fornecedor;
        }
    }

    static class EstoqueTableModel extends AbstractTableModel {
        private final String[] cols = {"Nome", "Lote", "Quantidade", "Validade", "Preço", "Fornecedor"};
        private final Class<?>[] types = {String.class, String.class, Integer.class, LocalDate.class, BigDecimal.class, String.class};

        final List<Medicamento> data = new ArrayList<>();

        public Medicamento getAt(int row) {
            return data.get(row);
        }

        public void add(Medicamento m) {
            data.add(m);
            int idx = data.size() - 1;
            fireTableRowsInserted(idx, idx);
        }

        public void updateAt(int row, Medicamento m) {
            data.set(row, m);
            fireTableRowsUpdated(row, row);
        }

        public void removeAt(int row) {
            data.remove(row);
            fireTableRowsDeleted(row, row);
        }

        public void setAll(List<Medicamento> list) {
            data.clear();
            data.addAll(list);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return types[columnIndex];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Medicamento m = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> m.nome();
                case 1 -> m.lote();
                case 2 -> m.quantidade();
                case 3 -> m.validade();
                case 4 -> m.preco();
                case 5 -> m.fornecedor();
                default -> "";
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FarmaciaApp().setVisible(true));
    }

    static final class Masks {
        private Masks() {}

        static void installDateMask(JTextField field) {
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new DateMaskFilter(field));
        }

        static void installMoneyMask(JTextField field) {
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new MoneyMaskFilter(field));
        }

        private static final class DateMaskFilter extends DocumentFilter {
            private final JTextField field;

            private DateMaskFilter(JTextField field) {
                this.field = field;
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                replace(fb, offset, length, "", null);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);

                int digitsBeforeCaret = countDigits(next.substring(0, Math.min(next.length(), offset + (text == null ? 0 : text.length()))));

                String digits = onlyDigits(next);
                if (digits.length() > 8) digits = digits.substring(0, 8);

                String formatted = formatDateDigits(digits);
                fb.replace(0, fb.getDocument().getLength(), formatted, attrs);

                int caret = caretPosForDateDigits(formatted, digitsBeforeCaret);
                SwingUtilities.invokeLater(() -> field.setCaretPosition(Math.min(caret, field.getText().length())));
            }

            private static int countDigits(String s) {
                int c = 0;
                for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) c++;
                return c;
            }

            private static String onlyDigits(String s) {
                StringBuilder out = new StringBuilder(s.length());
                for (int i = 0; i < s.length(); i++) {
                    char ch = s.charAt(i);
                    if (Character.isDigit(ch)) out.append(ch);
                }
                return out.toString();
            }

            private static String formatDateDigits(String digits) {
                StringBuilder out = new StringBuilder(10);
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 2 || i == 4) out.append('/');
                    out.append(digits.charAt(i));
                }
                return out.toString();
            }

            private static int caretPosForDateDigits(String formatted, int digitsBeforeCaret) {
                int digitsSeen = 0;
                for (int i = 0; i < formatted.length(); i++) {
                    if (Character.isDigit(formatted.charAt(i))) digitsSeen++;
                    if (digitsSeen >= digitsBeforeCaret) return i + 1;
                }
                return formatted.length();
            }
        }

        private static final class MoneyMaskFilter extends DocumentFilter {
            private final JTextField field;

            private MoneyMaskFilter(JTextField field) {
                this.field = field;
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                replace(fb, offset, length, "", null);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);

                int digitsBeforeCaret = countDigits(next.substring(0, Math.min(next.length(), offset + (text == null ? 0 : text.length()))));

                String digits = onlyDigits(next);
                if (digits.length() > 14) digits = digits.substring(0, 14);

                String formatted = formatMoneyDigits(digits);
                fb.replace(0, fb.getDocument().getLength(), formatted, attrs);

                int caret = caretPosForMoneyDigits(formatted, digitsBeforeCaret);
                SwingUtilities.invokeLater(() -> field.setCaretPosition(Math.min(caret, field.getText().length())));
            }

            private static int countDigits(String s) {
                int c = 0;
                for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) c++;
                return c;
            }

            private static String onlyDigits(String s) {
                StringBuilder out = new StringBuilder(s.length());
                for (int i = 0; i < s.length(); i++) {
                    char ch = s.charAt(i);
                    if (Character.isDigit(ch)) out.append(ch);
                }
                return out.toString();
            }

            // digits "1" -> "1"; "12" -> "12"; "123" -> "1,23"; "1234" -> "12,34"
            private static String formatMoneyDigits(String digits) {
                if (digits.isEmpty()) return "";
                if (digits.length() <= 2) return digits;
                int split = digits.length() - 2;
                String intPart = digits.substring(0, split);
                String fracPart = digits.substring(split);
                return groupThousands(intPart) + "," + fracPart;
            }

            private static String groupThousands(String intPart) {
                intPart = stripLeadingZeros(intPart);
                if (intPart.isEmpty()) return "0";
                if (intPart.length() <= 3) return intPart;

                StringBuilder out = new StringBuilder(intPart.length() + (intPart.length() / 3));
                int len = intPart.length();
                int firstGroupLen = len % 3;
                if (firstGroupLen == 0) firstGroupLen = 3;

                out.append(intPart, 0, firstGroupLen);
                for (int i = firstGroupLen; i < len; i += 3) {
                    out.append('.').append(intPart, i, i + 3);
                }
                return out.toString();
            }

            private static String stripLeadingZeros(String s) {
                int i = 0;
                while (i < s.length() && s.charAt(i) == '0') i++;
                return s.substring(i);
            }

            private static int caretPosForMoneyDigits(String formatted, int digitsBeforeCaret) {
                int digitsSeen = 0;
                for (int i = 0; i < formatted.length(); i++) {
                    if (Character.isDigit(formatted.charAt(i))) digitsSeen++;
                    if (digitsSeen >= digitsBeforeCaret) return i + 1;
                }
                return formatted.length();
            }
        }
    }
}