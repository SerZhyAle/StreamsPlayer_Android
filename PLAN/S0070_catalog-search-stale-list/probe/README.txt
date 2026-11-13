S0070 scratch probes, kept as the evidence of ticket section 3.2.

DiffProbe.java   - DiffUtil.calculateDiff cost on the catalog's row-key shape (a string built per comparison).
BudgetProbe.java - comparisons a callback-count budget would need per scenario, and how long each took.
outputs.txt      - the output of both on the host JVM, 2026-11-13.

Reproduce (JDK 21; classes.jar is extracted from recyclerview-1.3.2.aar of the Gradle cache):
  javac -cp classes.jar -d out DiffProbe.java BudgetProbe.java
  java -cp "out;classes.jar" DiffProbe 1500 6000 19550
  java -cp "out;classes.jar" BudgetProbe 1000 19550
Do not pass 119550 to BudgetProbe: its "to 15 scattered" case is quadratic and runs for about ten seconds.
