import java.io.*;
import java.nio.file.*;
import java.util.*;

// проверка запросов и хранения на отдельной временной базе
public class Tests {
    static int checks = 0;
    static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    static void run(String sql) throws Exception { Main.execute(sql); }
    static void error(String sql) throws Exception {
        boolean failed = false;
        try { run(sql); } catch (Exception e) { failed = true; }
        check(failed, "ожидалась ошибка: " + sql);
    }
    static int count(String table, String where, boolean index) throws Exception {
        Main.Table t = Main.db.table(table);
        return Main.find(t, Main.condition(t, where), index).size();
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("java-db-test-");
        Main.path = dir.resolve("database.dat").toString();
        Main.db = Main.load();
        try {
            check(Main.db.tables.size() == 2, "две начальные таблицы");
            run("SHOW TABLES;");
            error("SHOW TABLES extra;");
            run("INSERT INTO groups VALUES (1, 'Группа'), (2, 'Вторая');");
            run("INSERT INTO students VALUES (1, 'Анна', 8.5, '2005-05-10', TRUE, 1), (2, 'Иван', 7.0, NULL, FALSE, 1);");
            run("INSERT INTO students (id, name) VALUES (3, 'O''Brien, A');");
            check(count("students", null, false) == 3, "несколько записей и частичный INSERT");
            check(count("students", "name = 'O''Brien, A'", false) == 1, "строка с кавычкой и запятой");
            check(count("students", "birth_date = '2005-05-10'", false) == 1, "дата");
            check(count("students", "active = FALSE", false) == 1, "boolean");
            check(count("students", "grade > 7", false) == 1, "double и сравнение");
            check(count("students", "id >= 2", false) == 2, "целые и сравнение");
            error("INSERT INTO students (id, name) VALUES (4, 'ok'), (1, 'duplicate');");
            check(count("students", null, false) == 3, "откат всей вставки при ошибке ключа");
            error("INSERT INTO students (id, group_id) VALUES (4, 99);");
            error("INSERT INTO students (id, birth_date) VALUES (4, '2025-02-29');");
            error("INSERT INTO students (id, active) VALUES (4, maybe);");
            error("INSERT INTO students (id, grade) VALUES (4, 'abc');");
            error("INSERT INTO students (name) VALUES ('без ключа');");
            error("DELETE FROM groups WHERE id = 1;");
            error("UPDATE groups SET id = 9 WHERE id = 1;");
            error("UPDATE students SET id = 1 WHERE id = 2;");
            check(count("groups", "id = 1", false) == 1, "откат удаления и обновления");
            run("UPDATE students SET name = 'some WHERE text' WHERE id = 3;");
            check(count("students", "name = 'some WHERE text'", false) == 1, "WHERE внутри строкового литерала");
            run("CREATE INDEX idx_name ON students (name);");
            run("CREATE INDEX idx_grade ON students (grade);");
            for (String condition : Arrays.asList("name = 'Анна'", "name = 'нет'", "name = NULL", "grade > 7", "grade <= 8.5"))
                check(Main.find(Main.db.table("students"), Main.condition(Main.db.table("students"), condition), true).equals(Main.find(Main.db.table("students"), Main.condition(Main.db.table("students"), condition), false)), "индекс и перебор: " + condition);
            run("UPDATE students SET name = 'Мария', grade = 9 WHERE id = 2;");
            check(count("students", "name = 'Мария'", true) == 1, "индекс после UPDATE");
            run("DELETE FROM students WHERE id = 1;");
            check(count("students", "name = 'Мария'", true) == 1, "индекс после DELETE и сдвига строк");
            run("SELECT name, grade FROM students WHERE grade >= 9;");
            run("CREATE TABLE courses (id INT PRIMARY KEY, title STRING, group_id INTEGER REFERENCES groups(id));");
            run("INSERT INTO courses VALUES (1, 'Java', 2);");
            error("DROP TABLE groups;");
            error("CREATE TABLE broken (id INTEGER, text STRING);");
            error("CREATE TABLE broken (id INTEGER PRIMARY KEY, other INTEGER REFERENCES groups(name));");
            run("DROP TABLE courses;");
            check(!Main.db.tables.containsKey("courses"), "DROP TABLE");
            Main.db = Main.load(); Main.db.validate(); Main.db.rebuild();
            check(count("students", "name = 'Мария'", true) == 1, "данные и индекс после перезапуска");
            check(Main.db.indexes.containsKey("idx_name"), "описание индекса сохранено");
            run("DELETE FROM students WHERE id >= 2;");
            check(count("students", null, false) == 0, "DELETE нескольких записей");
            run("UPDATE groups SET name = 'Общая';");
            check(count("groups", "name = 'Общая'", false) == 2, "UPDATE без WHERE");
            run("DELETE FROM groups;");
            check(count("groups", null, false) == 0, "DELETE без WHERE");
            error("NOT SQL;");
            System.out.println("Проверок пройдено: " + checks);
        } finally {
            Files.deleteIfExists(dir.resolve("database.dat.tmp"));
            Files.deleteIfExists(dir.resolve("database.dat"));
            Files.deleteIfExists(dir);
        }
    }
}
