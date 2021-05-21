package info.kgeorgiy.ja.bozhe.concurrent;

import java.util.ArrayList;
import java.util.List;

public class ThreadsRunner {
    public final List<Thread> threads;

    public ThreadsRunner(int threadsNumber) {
        if (threadsNumber <= 0) {
            throw new IllegalArgumentException("There should be positive number of threads.");
        }
        this.threads = new ArrayList<>(threadsNumber);
    }

    public ThreadsRunner(int threadsNumber, Runnable runnable) {
        if (threadsNumber <= 0) {
            throw new IllegalArgumentException("There should be positive number of threads.");
        }
        this.threads = new ArrayList<>(threadsNumber);
        for (int i = 0; i < threadsNumber; ++i) {
            run(runnable);
        }
    }

    public void run(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.start();
        threads.add(t);
    }

    public void join() throws InterruptedException {
        InterruptedException exception = null;
        for (int i = 0; i < threads.size(); ++i) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                if (exception == null) {
                    exception = new InterruptedException("Joining was interrupted.");
                    threads.forEach(Thread::interrupt);
                }
                i--;
                exception.addSuppressed(e);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    public void interrupt() {
        threads.forEach(Thread::interrupt);
        try {
            join();
        } catch (InterruptedException ignored) {}
    }
}
