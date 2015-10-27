package org.cubekode.codedome;

public class Sample {

  public static void main(String[] args) {

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
  }
}
