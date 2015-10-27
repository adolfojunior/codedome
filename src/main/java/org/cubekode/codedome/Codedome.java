package org.cubekode.codedome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Codedome implements AutoCloseable {

  private static BigDecimal ONE_MILLIS_IN_NANOS = BigDecimal.valueOf(TimeUnit.MILLISECONDS
      .toNanos(1));
  private static BigDecimal ONE_SECOND_IN_MILLIS = BigDecimal.valueOf(TimeUnit.SECONDS.toMillis(1));

  private static final double TWO_PERCENT = 0.02;

  private static final Callable<Object> WARM_TASK = new Callable<Object>() {
    @Override
    public Object call() throws Exception {
      return null;
    }
  };

  public static Codedome toAvailableProcessors() {
    return withThreads(Runtime.getRuntime().availableProcessors());
  }

  public static Codedome withThreads(final int theradCount) {
    return new Codedome(theradCount);
  }

  private int threadCount;
  private ExecutorService executorService;

  private boolean verboseResult = true;
  private boolean verboseInForks = false;
  private boolean verboseInTransaction = false;

  public boolean isVerboseResult() {
    return verboseResult;
  }

  public Codedome setVerboseResult(boolean verboseResult) {
    this.verboseResult = verboseResult;
    return this;
  }

  public boolean isVerboseInForks() {
    return verboseInForks;
  }

  public Codedome setVerboseInForks(boolean verboseInForks) {
    this.verboseInForks = verboseInForks;
    return this;
  }

  public boolean isVerboseInTransaction() {
    return verboseInTransaction;
  }

  public Codedome setVerboseInTransaction(boolean verboseInTransaction) {
    this.verboseInTransaction = verboseInTransaction;
    return this;
  }

  public Codedome(final int threadCount) {
    if (threadCount < 1) {
      throw new IllegalArgumentException("threadCount must be greater then 0");
    }
    this.threadCount = threadCount;
    this.executorService = Executors.newFixedThreadPool(threadCount);
    this.warmExecutor();
  }

  private void warmExecutor() {
    // warm threads for execution.
    try {
      this.executorService.invokeAll(Stream.generate(() -> WARM_TASK).limit(threadCount * 2)
          .collect(Collectors.toList()));
    } catch (InterruptedException e) {
      throw new IllegalStateException("Executor was interrupted");
    }
  }

  public BigDecimal execute(final String name, final int count, final Runnable task) {
    try {
      BigDecimal average = executeAll(count, task);
      BigDecimal throughput = calculateThroughput(average);
      if (verboseResult) {
        System.out.printf(
            "# %s => [threads: %s, trans: %s, throughput(trans/sec): %s, avg-time(ms): %s]%n",
            name, threadCount, count, throughput, average);
      }
      return average;
    } catch (Exception e) {
      throw new IllegalStateException("Test fail", e);
    }
  }

  public BigDecimal executeAll(final int count, final Runnable task) throws InterruptedException {

    List<TaskFork> forks = forks(count, task);

    return executorService.invokeAll(forks).stream().map((future) -> {
      try {
        return future.get();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }).reduce(BigDecimal.ZERO, (prev, next) -> prev.add(next))
        .divide(BigDecimal.valueOf(forks.size()), RoundingMode.HALF_DOWN);
  }

  /**
   * 14 execs for 3 threads x = block o = residue t1: xxxxo t2: xxxxo t3: xxxx
   */
  private List<TaskFork> forks(final int count, final Runnable task) {

    int size = count / threadCount;
    int residue = count % threadCount;
    // executions peer thread
    final List<TaskFork> tasks = new ArrayList<>(threadCount);
    for (int i = 0; i < threadCount; i++, residue--) {
      int forkSize = size + (residue > 0 ? 1 : 0);
      if (forkSize > 0) {
        tasks.add(new TaskFork(i, forkSize, task));
      } else {
        break;
      }
    }
    return tasks;
  }

  private BigDecimal calculateThroughput(BigDecimal average) {
    return ONE_SECOND_IN_MILLIS.divide(average, RoundingMode.HALF_DOWN);
  }

  class TaskFork implements Callable<BigDecimal> {

    private int forkId;
    private int count;
    private Runnable task;

    private BigDecimal total;
    private int percentileSize;
    private List<BigDecimal> percentileList;

    public TaskFork(final int forkId, final int count, final Runnable task) {
      this.forkId = forkId;
      this.count = count;
      this.task = task;
      this.total = BigDecimal.ZERO;
      this.percentileSize = Math.max(1, (int) (count * TWO_PERCENT));
      this.percentileList = new ArrayList<>(percentileSize);
    }

    @Override
    public BigDecimal call() throws Exception {
      return execute(count, task);
    }

    private BigDecimal execute(final int count, final Runnable localTask) {

      long timeNanos = 0;
      long timeMillis = 0;

      for (int exec = 0; exec < count; exec++) {

        timeMillis = System.currentTimeMillis();
        timeNanos = System.nanoTime();

        localTask.run();

        timeNanos = System.nanoTime() - timeNanos;
        timeMillis = System.currentTimeMillis() - timeMillis;

        computeExecution(exec, BigDecimal.valueOf(timeMillis), BigDecimal.valueOf(timeNanos));
      }
      return endExecution();
    }

    private void computeExecution(int exec, BigDecimal millis, BigDecimal nanos) {

      BigDecimal current = millis.add(nanos.divide(ONE_MILLIS_IN_NANOS).remainder(BigDecimal.ONE));

      computePercentile(current);

      total = total.add(current);

      if (verboseInTransaction) {
        System.out.printf("# - (%s:%s) [time(ns): %s {%s}, max-time(ms): %s]%n", forkId, exec,
            current, total, percentileList.get(0));
      }
    }

    private void computePercentile(BigDecimal value) {
      // add until fill
      if (percentileList.size() < percentileSize) {
        percentileList.add(value);
      } else if (percentileList.get(percentileList.size() - 1).compareTo(value) == -1) {
        // only if the value is bigger then the last value in the list
        percentileList.set(percentileSize - 1, value);
      } else {
        // no add no sort
        return;
      }
      percentileList.sort((a, b) -> b.compareTo(a));
    }

    private BigDecimal endExecution() {
      // remove the biggest results to avoid outliers
      if (count > percentileList.size()) {
        for (BigDecimal percentile : percentileList) {
          total = total.subtract(percentile);
        }
        total =
            total.divide(BigDecimal.valueOf(count - percentileList.size()), RoundingMode.HALF_DOWN);
      } else {
        total = total.divide(BigDecimal.valueOf(count), RoundingMode.HALF_DOWN);
      }
      if (verboseInForks) {
        System.out.printf("# fork (%s) [trans: %s, throughput(trans/sec): %s, avg-time(ms): %s]%n",
            forkId, count, calculateThroughput(total), total);
      }
      return total;
    }
  }

  @Override
  public void close() throws Exception {
    executorService.shutdown();
  }
}
