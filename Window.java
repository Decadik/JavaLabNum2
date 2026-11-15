import java.awt.BorderLayout;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import javax.swing.*;

// простое окно для ввода запросов к базе
public class Window {
    public static void main(String[] args) {
        try {
            Main.db = Main.load();
            Main.db.validate();
            Main.db.rebuild();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Не удалось загрузить базу: " + e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showWindow();
            }
        });
    }

    static void showWindow() {
        JFrame frame = new JFrame("СУБД");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);
        frame.setLocationRelativeTo(null);

        JTextArea query = new JTextArea(4, 40);
        JTextArea result = new JTextArea();
        result.setEditable(false);
        result.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JButton button = new JButton("Выполнить запрос");
        JButton tables = new JButton("Показать таблицы");
        JButton help = new JButton("Примеры запросов");

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(new JLabel("Введите один SQL-запрос:"), BorderLayout.NORTH);
        top.add(new JScrollPane(query), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(button);
        buttons.add(tables);
        buttons.add(help);
        top.add(buttons, BorderLayout.SOUTH);

        frame.add(top, BorderLayout.NORTH);
        frame.add(new JScrollPane(result), BorderLayout.CENTER);
        button.addActionListener(e -> {
            String sql = query.getText().trim();
            if (sql.isEmpty()) {
                result.setText("Введите запрос.");
                return;
            }
            PrintStream oldOut = System.out;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream output = new PrintStream(bytes, true, "UTF-8")) {
                System.setOut(output);
                Main.execute(sql);
                result.setText(bytes.toString("UTF-8"));
            } catch (Exception ex) {
                result.setText("Ошибка: " + ex.getMessage());
            } finally {
                System.setOut(oldOut);
            }
        });
        tables.addActionListener(e -> {
            query.setText("SHOW TABLES;");
            button.doClick();
        });
        help.addActionListener(e -> {
            result.setText("Примеры запросов:\n\n"
                + "SHOW TABLES;\n"
                + "SELECT * FROM groups;\n"
                + "INSERT INTO groups VALUES (1, 'Группа');\n"
                + "SELECT * FROM groups WHERE id = 1;\n"
                + "UPDATE groups SET name = 'Новая группа' WHERE id = 1;\n"
                + "DELETE FROM groups WHERE id = 1;\n"
                + "CREATE TABLE items (id INTEGER PRIMARY KEY, name STRING);\n"
                + "CREATE INDEX idx_items_name ON items (name);\n"
                + "DROP TABLE items;\n\n"
                + "Введите один запрос в верхнее поле и нажмите «Выполнить запрос».\n"
                + "Другие примеры и описание типов данных находятся в readme.md.");
        });
        frame.setVisible(true);
    }
}
