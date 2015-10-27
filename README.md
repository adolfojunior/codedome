# codedome
Dome to measure algorithms

- Java Sample
```Java
try (Codedome codedome = Codedome.withThreads(1)) {

  // codedome.setVerboseInForks(true).setVerboseInTransaction(true);

  codedome.execute("toString", 10000, new Runnable() {
    @Override
    public void run() {
      toString();
    }
  });

  codedome.execute("timeMillis", 10000, new Runnable() {
    @Override
    public void run() {
      System.currentTimeMillis();
    }
  });

  codedome.execute("nanoTime", 10000, new Runnable() {
    @Override
    public void run() {
      System.nanoTime();
    }
  });

} catch (Exception e) {
  e.printStackTrace();
}
```

Will print:
```
# toString => [threads: 1, trans: 10000, throughput(trans/sec): 1751313, avg-time(ms): 0.000571]
# timeMillis => [threads: 1, trans: 10000, throughput(trans/sec): 10101010, avg-time(ms): 0.000099]
# nanoTime => [threads: 1, trans: 10000, throughput(trans/sec): 9708738, avg-time(ms): 0.000103]
```















