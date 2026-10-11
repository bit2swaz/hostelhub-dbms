package com.hostelhub.ui;

import com.hostelhub.service.ServiceException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;

/** Small shared UI helpers: background work, messages, tables and formatting. */
public final class Ui {

    public static final Color ERROR = new Color(0xB3261E);
    public static final Color OVERDUE = new Color(0xFDE2E1);
    public static final Color MUTED = new Color(0x5F6368);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** A combo-box item: the value plus the text shown for it. */
    public record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private Ui() {
    }

    /**
     * Runs work off the Event Dispatch Thread, then done on it. A ServiceException's message goes
     * to onError; any other failure shows a generic message (details go to the log, never the user).
     */
    public static <T> void async(Callable<T> work, Consumer<T> done, Consumer<String> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    onError.accept(userMessage(e.getCause()));
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                done.accept(result);
            }
        }.execute();
    }

    public static <T> void async(Component parent, Callable<T> work, Consumer<T> done) {
        async(work, done, msg -> error(parent, msg));
    }

    static String userMessage(Throwable t) {
        if (t instanceof ServiceException) {
            return t.getMessage();
        }
        System.err.println("HostelHub: unexpected error");
        t.printStackTrace();
        return "Something went wrong. Please try again.";
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "HostelHub", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "HostelHub", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** The red inline message label every form and dialog uses. */
    public static JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setName("error");
        l.setForeground(ERROR);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 4f));
        return l;
    }

    /** A sortable table. Rows that match highlight get a light red background. */
    public static <T> JTable table(String name, ListTableModel<T> model, Predicate<T> highlight) {
        JTable t = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (c instanceof JComponent jc) {
                    Object v = getValueAt(row, column);
                    jc.setToolTipText(v == null || v.toString().isEmpty() ? null : v.toString());
                }
                if (!isRowSelected(row)) {
                    boolean hl = highlight != null && highlight.test(model.row(convertRowIndexToModel(row)));
                    c.setBackground(hl ? OVERDUE : getBackground());
                }
                return c;
            }

        };
        model.addTableModelListener(e -> javax.swing.SwingUtilities.invokeLater(() -> fitColumns(t)));
        t.setName(name);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setRowHeight(26);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        fitColumns(t);
        return t;
    }

    /** Sizes each column to its widest header or cell (first 500 rows). */
    static void fitColumns(JTable t) {
        for (int c = 0; c < t.getColumnCount(); c++) {
            javax.swing.table.TableColumn col = t.getColumnModel().getColumn(c);
            int w = t.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(t, col.getHeaderValue(), false, false, -1, c).getPreferredSize().width;
            for (int r = 0; r < Math.min(t.getRowCount(), 500); r++) {
                w = Math.max(w, t.prepareRenderer(t.getCellRenderer(r, c), r, c).getPreferredSize().width);
            }
            col.setPreferredWidth(w + 16);
            col.setMinWidth(w <= 150 ? w + 8 : 60);
        }
    }

    public static <T> JTable table(String name, ListTableModel<T> model) {
        return table(name, model, null);
    }

    /** The selected row's record, or null. */
    public static <T> T selected(JTable table, ListTableModel<T> model) {
        int view = table.getSelectedRow();
        return view < 0 ? null : model.row(table.convertRowIndexToModel(view));
    }

    public static void pad(JComponent c, int px) {
        c.setBorder(BorderFactory.createEmptyBorder(px, px, px, px));
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String date(LocalDateTime d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String gender(String g) {
        return "M".equals(g) ? "Male" : "F".equals(g) ? "Female" : g;
    }
}
