package farmacia.ui;

import farmacia.model.Medicamento;
import farmacia.util.MedicamentoValidator;
import farmacia.util.ValidadeUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;

import static farmacia.util.AppConstants.DATE_FMT;
import static farmacia.util.TableColumns.PRECO;
import static farmacia.util.TableColumns.VALIDADE;

public class ExpirationCellRenderer extends DefaultTableCellRenderer {

    private static final Color COR_VENCENDO = new Color(205, 102, 0);

    private final EstoqueTableModel tableModel;
    private final JSpinner diasSpinner;

    public ExpirationCellRenderer(EstoqueTableModel tableModel, JSpinner diasSpinner) {
        this.tableModel = tableModel;
        this.diasSpinner = diasSpinner;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        int modelColumn = table.convertColumnIndexToModel(column);

        if (modelColumn == VALIDADE && value instanceof LocalDate d) {
            setText(d.format(DATE_FMT));
        } else if (modelColumn == PRECO && value instanceof BigDecimal preco) {
            setText(MedicamentoValidator.formatPreco(preco));
        }

        int modelRow = table.convertRowIndexToModel(row);
        Medicamento m = tableModel.getAt(modelRow);
        int nDias = (Integer) diasSpinner.getValue();

        if (isSelected) {
            c.setForeground(Color.WHITE);
        } else if (ValidadeUtils.isVencido(m)) {
            c.setForeground(Color.RED);
        } else if (ValidadeUtils.venceEmBreve(m, nDias)) {
            c.setForeground(COR_VENCENDO);
        } else {
            c.setForeground(Color.BLACK);
        }

        return c;
    }
}
