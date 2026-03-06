import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class FarmaciaApp extends JFrame {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DEFAULT_CSV = "estoque_farmacia.csv";

    private final JTextField nomeField = new JTextField(16);
    private final JTextField loteField = new JTextField(10);
    private final JSpinner qtdSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1_000_000, 1));
    private final JTextField validadeField = new JTextField(10);
    private final JTextField precoField = new JTextField(8);
    private final JTextField fornecedorField = new JTextField(14);

    private final JButton addBtn = new JButton("Cadastrar");
    private final JButton editBtn = new JButton("Editar selecionado");
    private final JButton removeBtn = new JButton("Remover selecionado");
    private final JButton saveBtn = new JButton("Salvar CSV");
    private final JButton loadBtn = new JButton("Carregar CSV");

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

        addBtn.addActionListener(e -> cadastrar());
        editBtn.addActionListener(e -> editarSelecionado());
        removeBtn.addActionListener(e -> removerSelecionado());
        saveBtn.addActionListener(e -> salvarCSVAtomico(DEFAULT_CSV, true));
        loadBtn.addActionListener(e -> carregarCSV(DEFAULT_CSV, true));

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
                salvarCSVAtomico(DEFAULT_CSV, false);
                dispose();
                System.exit(0);
            }
        });

        carregarCSV(DEFAULT_CSV, false);
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

        c.gridx = 2; c.gridy = y; p.add(new JLabel("Preço (ex: 12.50):"), c);
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

        TableCellRenderer base = table.getDefaultRenderer(Object.class);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

                Component c = ((DefaultTableCellRenderer) base)
                        .getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                int modelRow = table.convertRowIndexToModel(row);
                Medicamento m = tableModel.getAt(modelRow);

                LocalDate hoje = LocalDate.now();
                int nDias = (Integer) diasSpinner.getValue();

                boolean vencido = m.validade.isBefore(hoje);
                boolean vencendoEmBreve = !vencido && !m.validade.isAfter(hoje.plusDays(nDias));

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
                preco = new BigDecimal(precoStr.replace(',', '.'));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Preço inválido. Ex: 12.50");
                return;
            }
        }

        Medicamento m = new Medicamento(nome, lote, qtd, validade, preco, fornecedor);
        tableModel.add(m);

        limparFormulario();
        aplicarFiltro();
        atualizarContadores();
        table.repaint();

        salvarCSVAtomico(DEFAULT_CSV, false);
    }

    private void editarSelecionado() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha para editar.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        Medicamento atual = tableModel.getAt(modelRow);

        JTextField nome = new JTextField(atual.nome);
        JTextField lote = new JTextField(atual.lote);
        JSpinner qtd = new JSpinner(new SpinnerNumberModel(atual.quantidade, 0, 1_000_000, 1));
        JTextField validade = new JTextField(atual.validade.format(FMT));
        JTextField preco = new JTextField(atual.preco.toPlainString());
        JTextField fornecedor = new JTextField(atual.fornecedor);

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
                precoValue = new BigDecimal(precoStr.replace(',', '.'));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Preço inválido. Ex: 12.50");
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

        salvarCSVAtomico(DEFAULT_CSV, false);
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

        salvarCSVAtomico(DEFAULT_CSV, false);
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
                    return m.validade.isBefore(hoje);
                }
            });
        } else if (rbAteNDias.isSelected()) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends EstoqueTableModel, ? extends Integer> entry) {
                    Medicamento m = tableModel.getAt(entry.getIdentifier());
                    boolean vencido = m.validade.isBefore(hoje);
                    boolean ateN = !m.validade.isAfter(hoje.plusDays(nDias));
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
        long vencidos = tableModel.data.stream().filter(m -> m.validade.isBefore(hoje)).count();
        long ateN = tableModel.data.stream().filter(m -> {
            boolean vencido = m.validade.isBefore(hoje);
            boolean prox = !m.validade.isAfter(hoje.plusDays(nDias));
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

    private void salvarCSVAtomico(String path, boolean mostrarMensagem) {
        File tmp = new File(path + ".tmp");
        File out = new File(path);

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {

            w.write("nome;lote;quantidade;validade;preco;fornecedor");
            w.newLine();

            for (Medicamento m : tableModel.data) {
                w.write(csv(m.nome)); w.write(';');
                w.write(csv(m.lote)); w.write(';');
                w.write(Integer.toString(m.quantidade)); w.write(';');
                w.write(m.validade.format(FMT)); w.write(';');
                w.write(m.preco.toPlainString()); w.write(';');
                w.write(csv(m.fornecedor));
                w.newLine();
            }

            w.flush();
        } catch (IOException e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar CSV: " + e.getMessage());
            }
            if (tmp.exists()) tmp.delete();
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
                JOptionPane.showMessageDialog(this, "Salvo em: " + path);
            }
        } catch (IOException e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro final ao salvar CSV: " + e.getMessage());
            }
        }
    }

    private void carregarCSV(String path, boolean mostrarMensagem) {
        File f = new File(path);
        if (!f.exists()) {
            return;
        }

        List<Medicamento> novos = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {

            String line = r.readLine();
            if (line == null) {
                if (mostrarMensagem) {
                    JOptionPane.showMessageDialog(this, "CSV vazio.");
                }
                return;
            }

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = splitCsvLine(line, ';');
                if (parts.length < 6) continue;

                String nome = uncsv(parts[0]);
                String lote = uncsv(parts[1]);
                int qtd = Integer.parseInt(parts[2]);
                LocalDate validade = LocalDate.parse(parts[3], FMT);
                BigDecimal preco = new BigDecimal(parts[4]);
                String fornecedor = uncsv(parts[5]);

                novos.add(new Medicamento(nome, lote, qtd, validade, preco, fornecedor));
            }

            tableModel.setAll(novos);
            aplicarFiltro();
            atualizarContadores();
            table.repaint();

            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Carregado de: " + path);
            }

        } catch (Exception e) {
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar CSV: " + e.getMessage());
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"");
        if (t.contains(";") || t.contains("\"") || t.contains("\n")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private static String uncsv(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    private static String[] splitCsvLine(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

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

    static class Medicamento {
        final String nome;
        final String lote;
        final int quantidade;
        final LocalDate validade;
        final BigDecimal preco;
        final String fornecedor;

        Medicamento(String nome, String lote, int quantidade, LocalDate validade, BigDecimal preco, String fornecedor) {
            this.nome = nome;
            this.lote = lote == null ? "" : lote;
            this.quantidade = quantidade;
            this.validade = validade;
            this.preco = preco == null ? BigDecimal.ZERO : preco;
            this.fornecedor = fornecedor == null ? "" : fornecedor;
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
                case 0 -> m.nome;
                case 1 -> m.lote;
                case 2 -> m.quantidade;
                case 3 -> m.validade;
                case 4 -> m.preco;
                case 5 -> m.fornecedor;
                default -> "";
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FarmaciaApp().setVisible(true));
    }
}