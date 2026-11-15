import java.util.*;

// сравнение поиска перебором и по индексу
public class Benchmark {
    public static void main(String[] args) throws Exception {
        Main.db = new Main.Database();
        Main.Table t = new Main.Table("items");
        Main.Column id = new Main.Column("id", "INTEGER"); id.primary = true; t.columns.add(id);
        for (int i = 0; i < 100000; i++) {
            ArrayList<Object> row = new ArrayList<Object>(); row.add(i); t.rows.add(row);
        }
        Main.db.tables.put(t.name, t);
        Main.db.indexes.put("idx", new Main.Index("idx", "items", "id")); Main.db.rebuild();
        Main.Condition c = Main.condition(t, "id = 99999");
        for (int i = 0; i < 200; i++) { Main.find(t,c,false); Main.find(t,c,true); }
        int count = 1000; long start = System.nanoTime();
        for (int i = 0; i < count; i++) Main.find(t,c,false);
        long scan = System.nanoTime() - start; start = System.nanoTime();
        for (int i = 0; i < count; i++) Main.find(t,c,true);
        long index = System.nanoTime() - start;
        System.out.printf(Locale.US, "100000 строк, %d поисков: без индекса %.3f мс, с индексом %.3f мс%n", count, scan / 1000000.0, index / 1000000.0);
    }
}
