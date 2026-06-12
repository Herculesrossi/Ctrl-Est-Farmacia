package farmacia;

import farmacia.model.Medicamento;
import farmacia.repository.EstoqueRepository;
import farmacia.ui.EstoqueTableModel;
import farmacia.ui.ExpirationCellRenderer;
import farmacia.util.Masks;
import farmacia.util.MedicamentoValidator;
import farmacia.util.ValidadeUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static farmacia.util.AppConstants.DATE_FMT;
import static farmacia.util.AppConstants.DEFAULT_JSON;
import static farmacia.util.MedicamentoValidator.formatPreco;
import static farmacia.util.TableColumns.VALIDADE;

public class FarmaciaApp extends JFrame {

    private final EstoqueRepository repository = new EstoqueRepository();

    private final JTextField nomeField = new JTextField(16);
    private final JTextField loteField = new JTextField(10);
    private final JSpinner qtdSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
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
        saveBtn.addActionListener(e -> salvarJSON(true));
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
                if (!salvarJSON(false)) {
                    int opcao = JOptionPane.showConfirmDialog(
                            FarmaciaApp.this,
                            "Não foi possível salvar o estoque. Deseja sair mesmo assim?",
                            "Erro ao salvar",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (opcao != JOptionPane.YES_OPTION) return;
                }
                dispose();
            }
        });

        migrarCsvLegadoSeNecessario();
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

        TableCellRenderer expirationRenderer = new ExpirationCellRenderer(tableModel, diasSpinner);
        for (int col = 0; col < table.getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(expirationRenderer);
        }

        sorter.setComparator(VALIDADE, (a, b) -> {
            LocalDate da = (LocalDate) a;
            LocalDate db = (LocalDate) b;
            return da.compareTo(db);
        });
    }

    private void cadastrar() {
        MedicamentoValidator.Result validacao = validarFormulario();
        if (!validacao.valid()) {
            JOptionPane.showMessageDialog(this, validacao.message());
            return;
        }

        int qtd = (Integer) qtdSpinner.getValue();
        Medicamento m = new Medicamento(
                nomeField.getText().trim(),
                loteField.getText().trim(),
                qtd,
                validacao.validade(),
                validacao.preco(),
                fornecedorField.getText().trim()
        );
        tableModel.add(m);

        limparFormulario();
        refreshTable();
        salvarAutomatico();
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
        JSpinner qtd = new JSpinner(new SpinnerNumberModel(Math.max(1, atual.quantidade()), 1, 1_000_000, 1));
        JTextField validade = new JTextField(atual.validade().format(DATE_FMT));
        JTextField preco = new JTextField(formatPreco(atual.preco()));
        JTextField fornecedor = new JTextField(atual.fornecedor());

        Masks.installDateMask(validade);
        Masks.installMoneyMask(preco);

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

        MedicamentoValidator.Result validacao = MedicamentoValidator.validate(
                nome.getText().trim(),
                lote.getText().trim(),
                (Integer) qtd.getValue(),
                validade.getText().trim(),
                preco.getText().trim(),
                fornecedor.getText().trim()
        );
        if (!validacao.valid()) {
            JOptionPane.showMessageDialog(this, validacao.message());
            return;
        }

        Medicamento novo = new Medicamento(
                nome.getText().trim(),
                lote.getText().trim(),
                (Integer) qtd.getValue(),
                validacao.validade(),
                validacao.preco(),
                fornecedor.getText().trim()
        );

        tableModel.updateAt(modelRow, novo);
        refreshTable();
        salvarAutomatico();
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

        refreshTable();
        salvarAutomatico();
    }

    private MedicamentoValidator.Result validarFormulario() {
        return MedicamentoValidator.validate(
                nomeField.getText().trim(),
                loteField.getText().trim(),
                (Integer) qtdSpinner.getValue(),
                validadeField.getText().trim(),
                precoField.getText().trim(),
                fornecedorField.getText().trim()
        );
    }

    private void aplicarFiltro() {
        if (rbTodos.isSelected()) {
            sorter.setRowFilter(null);
            atualizarContadores();
            return;
        }

        int nDias = (Integer) diasSpinner.getValue();

        if (rbVencidos.isSelected()) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends EstoqueTableModel, ? extends Integer> entry) {
                    Medicamento m = tableModel.getAt(entry.getIdentifier());
                    return ValidadeUtils.isVencido(m);
                }
            });
        } else if (rbAteNDias.isSelected()) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends EstoqueTableModel, ? extends Integer> entry) {
                    Medicamento m = tableModel.getAt(entry.getIdentifier());
                    return ValidadeUtils.venceEmBreve(m, nDias);
                }
            });
        }

        atualizarContadores();
    }

    private void atualizarContadores() {
        int nDias = (Integer) diasSpinner.getValue();
        int total = tableModel.getRowCount();
        long vencidos = ValidadeUtils.contarVencidos(tableModel.getMedicamentos());
        long ateN = ValidadeUtils.contarVencendoEm(tableModel.getMedicamentos(), nDias);

        infoLabel.setText(String.format(
                "Total: %d | Vencidos: %d | Vencendo em até %d dias: %d",
                total, vencidos, nDias, ateN
        ));
    }

    private void refreshTable() {
        aplicarFiltro();
        atualizarContadores();
        table.repaint();
    }

    private void limparFormulario() {
        nomeField.setText("");
        loteField.setText("");
        qtdSpinner.setValue(1);
        validadeField.setText("");
        precoField.setText("");
        fornecedorField.setText("");
    }

    private void migrarCsvLegadoSeNecessario() {
        Optional<List<Medicamento>> migrados = repository.migrarCsvLegadoSeNecessario();
        if (migrados.isEmpty()) return;

        tableModel.setAll(migrados.get());
        salvarAutomatico();
    }

    private void salvarAutomatico() {
        if (!salvarJSON(false)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível salvar o estoque automaticamente. Use o botão Salvar para tentar novamente.",
                    "Erro ao salvar",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private boolean salvarJSON(boolean mostrarMensagem) {
        try {
            repository.salvar(tableModel.getMedicamentos());
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Salvo em: " + DEFAULT_JSON);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao salvar JSON: " + e.getMessage());
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar JSON: " + e.getMessage());
            }
            return false;
        }
    }

    private void carregarJSON(boolean mostrarMensagem) {
        try {
            if (mostrarMensagem) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Carregar irá substituir os dados exibidos pelo conteúdo de " + DEFAULT_JSON + ". Deseja continuar?",
                        "Confirmar carregamento",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            Optional<EstoqueRepository.LoadResult> result = repository.carregar();
            if (result.isEmpty()) {
                if (mostrarMensagem) {
                    JOptionPane.showMessageDialog(this, "Arquivo não encontrado: " + DEFAULT_JSON);
                }
                return;
            }

            EstoqueRepository.LoadResult loadResult = result.get();
            tableModel.setAll(loadResult.medicamentos());
            refreshTable();

            if (loadResult.legacyFormat()) {
                salvarAutomatico();
            }

            if (loadResult.itensIgnorados() > 0) {
                String aviso = loadResult.itensIgnorados() + " item(ns) inválido(s) foram ignorados ao carregar.";
                System.err.println(aviso);
                if (mostrarMensagem) {
                    JOptionPane.showMessageDialog(this, aviso, "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }

            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Carregado de: " + DEFAULT_JSON);
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar JSON: " + e.getMessage());
            if (mostrarMensagem) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar JSON: " + e.getMessage());
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Não foi possível carregar o estoque: " + e.getMessage(),
                        "Erro ao iniciar",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FarmaciaApp().setVisible(true));
    }
}
