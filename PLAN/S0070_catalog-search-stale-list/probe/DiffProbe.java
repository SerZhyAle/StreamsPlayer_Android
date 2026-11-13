import androidx.recyclerview.widget.DiffUtil;
import java.util.ArrayList;
import java.util.List;

/** S0070 scratch probe: cost of DiffUtil.calculateDiff for the catalog's row key shape (string built per compare). */
public class DiffProbe {
    record Row(long id) {
        String key() { return "channel:" + id; }
    }

    static long run(List<Row> oldList, List<Row> newList) {
        long t0 = System.nanoTime();
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return oldList.size(); }
            public int getNewListSize() { return newList.size(); }
            public boolean areItemsTheSame(int o, int n) { return oldList.get(o).key().equals(newList.get(n).key()); }
            public boolean areContentsTheSame(int o, int n) { return oldList.get(o).equals(newList.get(n)); }
        });
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) {
        for (String a : args) {
            int n = Integer.parseInt(a);
            List<Row> all = new ArrayList<>();
            for (int i = 0; i < n; i++) all.add(new Row(i));
            List<Row> few = new ArrayList<>();
            for (int i = 0; i < 15; i++) few.add(all.get(i * (n / 15)));
            List<Row> sixty = new ArrayList<>();
            for (int i = 0; i < n; i++) if (i % 10 < 6) sixty.add(all.get(i));
            List<Row> moved = new ArrayList<>(all);
            moved.add(0, moved.remove(n / 2));
            System.out.println("N=" + n
                + " -> 15 scattered: " + run(all, few) + " ms"
                + " | -> 0 rows: " + run(all, new ArrayList<>()) + " ms"
                + " | -> 60% scattered: " + run(all, sixty) + " ms"
                + " | one row moved to top: " + run(all, moved) + " ms"
                + " | 15 -> N: " + run(few, all) + " ms");
        }
    }
}
