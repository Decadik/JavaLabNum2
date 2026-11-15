import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

// консольная программа для работы с таблицами
public class Main {
    static final String FILE = "database.dat";

    // описание столбца
    static class Column implements Serializable {
        private static final long serialVersionUID = 1L;
        String name, type, refTable, refColumn;
        boolean primary;
        Column(String name, String type) { this.name = name; this.type = type; }
    }

    // таблица со схемой и записями
    static class Table implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        ArrayList<Column> columns = new ArrayList<Column>();
        ArrayList<ArrayList<Object>> rows = new ArrayList<ArrayList<Object>>();
        Table(String name) { this.name = name; }
        int column(String name) {
            for (int i = 0; i < columns.size(); i++) if (columns.get(i).name.equalsIgnoreCase(name.trim())) return i;
            throw new IllegalArgumentException("неизвестный столбец: " + name);
        }
    }

    // индекс хранит номера записей для каждого значения
    static class Index implements Serializable {
        private static final long serialVersionUID = 1L;
        String name, table, column;
        HashMap<Object, ArrayList<Integer>> values = new HashMap<Object, ArrayList<Integer>>();
        Index(String name, String table, String column) { this.name = name; this.table = table; this.column = column; }
    }

    // данные и индексы сохраняются вместе в одном файле
    static class Database implements Serializable {
        private static final long serialVersionUID = 1L;
        LinkedHashMap<String, Table> tables = new LinkedHashMap<String, Table>();
        LinkedHashMap<String, Index> indexes = new LinkedHashMap<String, Index>();
        Table table(String name) {
            Table t = tables.get(name.toLowerCase());
            if (t == null) throw new IllegalArgumentException("таблица не найдена: " + name);
            return t;
        }
        void rebuild() {
            for (Index idx : indexes.values()) {
                idx.values.clear();
                Table t = table(idx.table); int col = t.column(idx.column);
                for (int i = 0; i < t.rows.size(); i++) {
                    Object value = t.rows.get(i).get(col);
                    if (!idx.values.containsKey(value)) idx.values.put(value, new ArrayList<Integer>());
                    idx.values.get(value).add(i);
                }
            }
        }
        void validate() {
            for (Table t : tables.values()) {
                for (int c = 0; c < t.columns.size(); c++) {
                    Column col = t.columns.get(c);
                    HashSet<Object> keys = new HashSet<Object>();
                    for (ArrayList<Object> row : t.rows) {
                        Object value = row.get(c);
                        if (col.primary && (value == null || !keys.add(value))) throw new IllegalArgumentException("первичный ключ должен быть непустым и уникальным: " + col.name);
                        if (col.refTable != null && value != null) {
                            Table parent = table(col.refTable); int p = parent.column(col.refColumn); boolean found = false;
                            for (ArrayList<Object> r : parent.rows) if (value.equals(r.get(p))) { found = true; break; }
                            if (!found) throw new IllegalArgumentException("нет связанной записи: " + col.name + " = " + value);
                        }
                    }
                }
            }
        }
    }

    static Database db;
    static String path = FILE;

    // загружает базу или создаёт начальные связанные таблицы
    static Database load() throws Exception {
        File f = new File(path);
        if (f.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) { return (Database) in.readObject(); }
        }
        Database d = new Database();
        Table groups = new Table("groups");
        Column id = new Column("id", "INTEGER"); id.primary = true;
        groups.columns.add(id); groups.columns.add(new Column("name", "STRING"));
        d.tables.put(groups.name, groups);
        Table students = new Table("students");
        id = new Column("id", "INTEGER"); id.primary = true;
        students.columns.add(id); students.columns.add(new Column("name", "STRING"));
        students.columns.add(new Column("grade", "DOUBLE"));
        students.columns.add(new Column("birth_date", "DATE"));
        students.columns.add(new Column("active", "BOOLEAN"));
        Column group = new Column("group_id", "INTEGER"); group.refTable = "groups"; group.refColumn = "id";
        students.columns.add(group);
        d.tables.put(students.name, students); return d;
    }

    // записывает состояние во временный файл и заменяет старый
    static void save() throws Exception {
        Path target = Paths.get(path).toAbsolutePath();
        Path temp = Paths.get(target.toString() + ".tmp");
        try (FileOutputStream file = new FileOutputStream(temp.toFile()); ObjectOutputStream out = new ObjectOutputStream(file)) {
            out.writeObject(db); out.flush(); file.getFD().sync();
        }
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    // преобразует литерал запроса в значение нужного типа
    static Object value(String text, String type) throws Exception {
        text = text.trim(); if (text.equalsIgnoreCase("NULL")) return null;
        if (type.equals("INTEGER")) return Integer.valueOf(text);
        if (type.equals("DOUBLE")) return Double.valueOf(text);
        if (type.equals("BOOLEAN")) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) throw new IllegalArgumentException("ожидается TRUE или FALSE");
            return Boolean.valueOf(text);
        }
        if (text.length() < 2 || text.charAt(0) != '\'' || text.charAt(text.length()-1) != '\'') throw new IllegalArgumentException("строку и дату нужно заключить в кавычки");
        String s = text.substring(1, text.length()-1).replace("''", "'");
        if (type.equals("DATE")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd"); format.setLenient(false);
            Date date = format.parse(s);
            if (!format.format(date).equals(s)) throw new IllegalArgumentException("дата должна быть в формате ГГГГ-ММ-ДД");
            return date;
        }
        return s;
    }

    // делит запрос на слова, значения и знаки
    static ArrayList<String> tokens(String sql) {
        ArrayList<String> result = new ArrayList<String>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            int start = i;
            if (c == '\'') {
                i++;
                boolean closed = false;
                while (i < sql.length()) {
                    if (sql.charAt(i) == '\'') {
                        i++;
                        if (i < sql.length() && sql.charAt(i) == '\'') i++;
                        else { closed = true; break; }
                    } else i++;
                }
                if (!closed) throw new IllegalArgumentException("незакрытая строка");
            } else if ("(),;*=<>!".indexOf(c) >= 0) {
                i++;
                if (i < sql.length() && ((c == '<' && sql.charAt(i) == '>') || ("<>=!".indexOf(c) >= 0 && sql.charAt(i) == '='))) i++;
            } else {
                while (i < sql.length() && !Character.isWhitespace(sql.charAt(i)) && "(),;*=<>!'".indexOf(sql.charAt(i)) < 0) i++;
            }
            result.add(sql.substring(start, i));
        }
        return result;
    }

    // последовательное чтение частей запроса
    static class Parser {
        ArrayList<String> words;
        int pos;
        Parser(String sql) { words = tokens(sql); }
        boolean has() { return pos < words.size(); }
        String next() {
            if (!has()) throw new IllegalArgumentException("запрос не закончен");
            return words.get(pos++);
        }
        boolean take(String word) {
            if (has() && words.get(pos).equalsIgnoreCase(word)) { pos++; return true; }
            return false;
        }
        void need(String word) {
            if (!take(word)) throw new IllegalArgumentException("ожидается " + word);
        }
        String name() {
            String word = next();
            if (word.isEmpty() || !Character.isLetter(word.charAt(0)) && word.charAt(0) != '_') throw new IllegalArgumentException("неверное имя: " + word);
            for (int i = 1; i < word.length(); i++) if (!Character.isLetterOrDigit(word.charAt(i)) && word.charAt(i) != '_') throw new IllegalArgumentException("неверное имя: " + word);
            return word.toLowerCase(Locale.ROOT);
        }
        void end() {
            take(";");
            if (has()) throw new IllegalArgumentException("лишняя часть запроса: " + next());
        }
    }

    static class Condition { int col; String op; Object val; }
    static Condition readCondition(Table t, Parser p) throws Exception {
        Condition c = new Condition();
        c.col = t.column(p.name());
        c.op = p.next();
        if (!Arrays.asList("=", "!=", "<>", "<", ">", "<=", ">=").contains(c.op)) throw new IllegalArgumentException("неизвестный оператор: " + c.op);
        c.val = value(p.next(), t.columns.get(c.col).type);
        return c;
    }
    // разбирает простое условие where
    static Condition condition(Table t, String text) throws Exception {
        if (text == null) return null;
        Parser p = new Parser(text);
        Condition c = readCondition(t, p);
        p.end();
        return c;
    }
    @SuppressWarnings("unchecked")
    // проверяет запись по условию
    static boolean matches(ArrayList<Object> row, Condition c) {
        if (c == null) return true; Object a = row.get(c.col), b = c.val;
        if (a == null || b == null) return c.op.equals("=") ? a == b : (c.op.equals("!=") || c.op.equals("<>")) && a != b;
        int n = ((Comparable<Object>) a).compareTo(b);
        if (c.op.equals("=")) return n == 0; if (c.op.equals("!=") || c.op.equals("<>")) return n != 0;
        if (c.op.equals("<")) return n < 0; if (c.op.equals(">")) return n > 0; if (c.op.equals("<=")) return n <= 0; return n >= 0;
    }
    // находит номера подходящих записей, используя индекс для равенства
    static ArrayList<Integer> find(Table t, Condition c, boolean useIndex) {
        ArrayList<Integer> candidates = null;
        if (useIndex && c != null && c.op.equals("=")) for (Index idx : db.indexes.values()) {
            if (idx.table.equals(t.name) && idx.column.equals(t.columns.get(c.col).name)) { candidates = idx.values.get(c.val); if (candidates == null) candidates = new ArrayList<Integer>(); break; }
        }
        ArrayList<Integer> result = new ArrayList<Integer>();
        if (candidates == null) { for (int i = 0; i < t.rows.size(); i++) if (matches(t.rows.get(i), c)) result.add(i); }
        else for (int i : candidates) if (matches(t.rows.get(i), c)) result.add(i);
        return result;
    }
    static String display(Object o) { if (o == null) return "NULL"; if (o instanceof Date) return new SimpleDateFormat("yyyy-MM-dd").format((Date)o); return o.toString(); }
    // вывод выбранных столбцов и записей таблицей
    static void select(Parser p) throws Exception {
        ArrayList<String> names = new ArrayList<String>();
        if (p.take("*")) names.add("*");
        else {
            names.add(p.name());
            while (p.take(",")) names.add(p.name());
        }
        p.need("FROM");
        Table t = db.table(p.name());
        Condition c = p.take("WHERE") ? readCondition(t, p) : null;
        p.end();
        ArrayList<Integer> cols = new ArrayList<Integer>();
        if (names.get(0).equals("*")) {
            for (int i = 0; i < t.columns.size(); i++) cols.add(i);
        } else {
            for (String name : names) cols.add(t.column(name));
        }
        ArrayList<Integer> rows = find(t, c, true);
        int[] widths = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            widths[i] = t.columns.get(cols.get(i)).name.length();
            for (int r : rows) widths[i] = Math.max(widths[i], display(t.rows.get(r).get(cols.get(i))).length());
        }
        for (int i = 0; i < cols.size(); i++) System.out.print(String.format("%-" + (widths[i] + 2) + "s", t.columns.get(cols.get(i)).name));
        System.out.println();
        for (int r : rows) {
            for (int i = 0; i < cols.size(); i++) System.out.print(String.format("%-" + (widths[i] + 2) + "s", display(t.rows.get(r).get(cols.get(i)))));
            System.out.println();
        }
        System.out.println("строк: " + rows.size());
    }

    static void insert(Parser p) throws Exception {
        p.need("INTO");
        Table t = db.table(p.name());
        ArrayList<Integer> cols = new ArrayList<Integer>();
        if (p.take("(")) {
            do {
                int col = t.column(p.name());
                if (cols.contains(col)) throw new IllegalArgumentException("столбец указан повторно");
                cols.add(col);
            } while (p.take(","));
            p.need(")");
        } else {
            for (int i = 0; i < t.columns.size(); i++) cols.add(i);
        }
        p.need("VALUES");
        do {
            p.need("(");
            ArrayList<Object> row = new ArrayList<Object>(Collections.nCopies(t.columns.size(), (Object) null));
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) p.need(",");
                int col = cols.get(i);
                row.set(col, value(p.next(), t.columns.get(col).type));
            }
            p.need(")");
            t.rows.add(row);
        } while (p.take(","));
        p.end();
    }

    static void update(Parser p) throws Exception {
        Table t = db.table(p.name());
        p.need("SET");
        HashMap<Integer, Object> changes = new HashMap<Integer, Object>();
        do {
            int col = t.column(p.name());
            p.need("=");
            changes.put(col, value(p.next(), t.columns.get(col).type));
        } while (p.take(","));
        Condition c = p.take("WHERE") ? readCondition(t, p) : null;
        p.end();
        for (ArrayList<Object> row : t.rows) {
            if (matches(row, c)) {
                for (Integer col : changes.keySet()) row.set(col, changes.get(col));
            }
        }
    }

    static void delete(Parser p) throws Exception {
        p.need("FROM");
        Table t = db.table(p.name());
        Condition c = p.take("WHERE") ? readCondition(t, p) : null;
        p.end();
        Iterator<ArrayList<Object>> it = t.rows.iterator();
        while (it.hasNext()) if (matches(it.next(), c)) it.remove();
    }

    static void create(Parser p) throws Exception {
        if (p.take("INDEX")) {
            String name = p.name();
            p.need("ON");
            Table t = db.table(p.name());
            p.need("(");
            int col = t.column(p.name());
            p.need(")");
            p.end();
            if (db.indexes.containsKey(name)) throw new IllegalArgumentException("индекс уже существует");
            db.indexes.put(name, new Index(name, t.name, t.columns.get(col).name));
        } else {
            p.need("TABLE");
            String name = p.name();
            if (db.tables.containsKey(name)) throw new IllegalArgumentException("таблица уже существует");
            Table t = new Table(name);
            int primary = 0;
            p.need("(");
            do {
                String colName = p.name();
                String type = p.next().toUpperCase(Locale.ROOT);
                if (type.equals("INT")) type = "INTEGER";
                if (!Arrays.asList("INTEGER", "DOUBLE", "STRING", "DATE", "BOOLEAN").contains(type)) throw new IllegalArgumentException("неизвестный тип: " + type);
                for (Column old : t.columns) if (old.name.equals(colName)) throw new IllegalArgumentException("столбец уже существует");
                Column col = new Column(colName, type);
                if (p.take("PRIMARY")) {
                    p.need("KEY");
                    col.primary = true;
                    primary++;
                } else if (p.take("REFERENCES")) {
                    Table parent = db.table(p.name());
                    p.need("(");
                    Column key = parent.columns.get(parent.column(p.name()));
                    p.need(")");
                    if (!key.primary || !key.type.equals(col.type)) throw new IllegalArgumentException("ссылка должна указывать на первичный ключ того же типа");
                    col.refTable = parent.name;
                    col.refColumn = key.name;
                }
                t.columns.add(col);
            } while (p.take(","));
            p.need(")");
            p.end();
            if (primary != 1) throw new IllegalArgumentException("нужен один первичный ключ");
            db.tables.put(name, t);
        }
    }

    static void drop(Parser p) {
        p.need("TABLE");
        Table t = db.table(p.name());
        p.end();
        for (Table other : db.tables.values()) {
            for (Column col : other.columns) {
                if (t.name.equals(col.refTable)) throw new IllegalArgumentException("на таблицу ссылается " + other.name);
            }
        }
        db.tables.remove(t.name);
        Iterator<Index> it = db.indexes.values().iterator();
        while (it.hasNext()) if (it.next().table.equals(t.name)) it.remove();
    }

    /** Выполняет запрос и сохраняет успешные изменения. */
    static void execute(String sql) throws Exception {
        Parser p = new Parser(sql);
        String command = p.next().toUpperCase(Locale.ROOT);
        if (command.equals("SELECT")) { select(p); return; }
        if (command.equals("SHOW")) {
            p.need("TABLES");
            p.end();
            System.out.println("Таблицы:");
            for (String name : db.tables.keySet()) System.out.println(name);
            System.out.println("всего: " + db.tables.size());
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(db); }
        try {
            if (command.equals("INSERT")) insert(p);
            else if (command.equals("UPDATE")) update(p);
            else if (command.equals("DELETE")) delete(p);
            else if (command.equals("CREATE")) create(p);
            else if (command.equals("DROP")) drop(p);
            else throw new IllegalArgumentException("неизвестная команда");
            db.validate();
            db.rebuild();
            save();
            System.out.println("готово");
        } catch (Exception e) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                db = (Database) in.readObject();
            }
            throw e;
        }
    }
    //запускает консоль, команда EXIT завершает работу
    public static void main(String[] args) {
        try { db = load(); db.validate(); db.rebuild(); }
        catch (Exception e) { System.out.println("не удалось загрузить базу: " + e.getMessage()); return; }
        System.out.println("СУБД. Введите SQL-запрос или EXIT для выхода.");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> "); if (!scanner.hasNextLine()) break; String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("exit;")) break;
                if (line.isEmpty()) continue;
                try { execute(line); } catch (Exception e) { System.out.println("ошибка: " + e.getMessage()); }
            }
        }
    }
}
