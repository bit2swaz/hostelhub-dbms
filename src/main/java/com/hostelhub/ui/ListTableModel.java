package com.hostelhub.ui;

import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/** Read-only table over a list of records; each record maps to one row of values. */
public class ListTableModel<T> extends AbstractTableModel {

    private final String[] columns;
    private final Function<T, Object[]> mapper;
    private List<T> rows = List.of();

    public ListTableModel(Function<T, Object[]> mapper, String... columns) {
        this.columns = columns;
        this.mapper = mapper;
    }

    public void setRows(List<T> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    public List<T> rows() {
        return rows;
    }

    public T row(int modelIndex) {
        return rows.get(modelIndex);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        return mapper.apply(rows.get(row))[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        for (T r : rows) {
            Object v = mapper.apply(r)[column];
            if (v != null) {
                return v.getClass();
            }
        }
        return Object.class;
    }
}
