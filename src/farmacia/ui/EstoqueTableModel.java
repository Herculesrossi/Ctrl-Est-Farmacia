package farmacia.ui;

import farmacia.model.Medicamento;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EstoqueTableModel extends AbstractTableModel {

    private final String[] cols = {"Nome", "Lote", "Quantidade", "Validade", "Preço", "Fornecedor"};
    private final Class<?>[] types = {
            String.class, String.class, Integer.class,
            LocalDate.class, BigDecimal.class, String.class
    };

    private final List<Medicamento> data = new ArrayList<>();

    public Medicamento getAt(int row) {
        return data.get(row);
    }

    public List<Medicamento> getMedicamentos() {
        return Collections.unmodifiableList(data);
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
