import androidx.recyclerview.widget.DiffUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** S0070 scratch probe: comparisons a budgeted DiffUtil run needs per scenario, allocation-free id compare. */
public class BudgetProbe {
    static final class Exceeded extends RuntimeException {
        @Override public Throwable fillInStackTrace() { return this; }
    }

    static String run(List<Integer> o, List<Integer> n, long budget) {
        long[] spent = {0};
        long t0 = System.nanoTime();
        String verdict;
        try {
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                public int getOldListSize() { return o.size(); }
                public int getNewListSize() { return n.size(); }
                public boolean areItemsTheSame(int a, int b) { if (++spent[0] > budget) throw new Exceeded(); return o.get(a).equals(n.get(b)); }
                public boolean areContentsTheSame(int a, int b) { if (++spent[0] > budget) throw new Exceeded(); return true; }
            });
            verdict = "diff";
        } catch (Exceeded e) {
            verdict = "RESET";
        }
        return verdict + " after " + Math.min(spent[0], budget) + " compares, " + (System.nanoTime() - t0) / 1_000_000 + " ms";
    }

    static List<Integer> range(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(i);
        return l;
    }

    static List<Integer> without(List<Integer> all, int count) {
        List<Integer> l = new ArrayList<>(all);
        int step = all.size() / count;
        for (int i = count - 1; i >= 0; i--) l.remove(i * step + step / 2);
        return l;
    }

    public static void main(String[] args) {
        long budget = 2_000_000L;
        for (String a : args) {
            int n = Integer.parseInt(a);
            List<Integer> all = range(n);
            List<Integer> moved = new ArrayList<>(all);
            moved.add(0, moved.remove(n / 2));
            List<Integer> inserted = new ArrayList<>(all);
            inserted.add(n / 2, -1);
            List<Integer> reversed = new ArrayList<>(all);
            Collections.reverse(reversed);
            System.out.println("N=" + n);
            System.out.println("  one row moved to top : " + run(all, moved, budget));
            System.out.println("  one row inserted     : " + run(all, inserted, budget));
            System.out.println("  10 scattered removed : " + run(all, without(all, 10), budget));
            System.out.println("  50 scattered removed : " + run(all, without(all, 50), budget));
            System.out.println("  200 scattered removed: " + run(all, without(all, 200), budget));
            System.out.println("  fully reversed       : " + run(all, reversed, budget));
            System.out.println("  to 15 scattered      : " + run(all, without(all, n - 15), budget));
        }
    }
}
