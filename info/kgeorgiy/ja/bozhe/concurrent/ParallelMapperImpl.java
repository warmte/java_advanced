package info.kgeorgiy.ja.bozhe.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

@SuppressWarnings("unused")
public class ParallelMapperImpl implements ParallelMapper {

    private final ThreadsRunner threadsRunner;
    private final TasksQueue taskQueue;
    private boolean closed = false;
    private InterruptedException exception;

    private void except(InterruptedException e) {
        if (exception == null) {
            exception = e;
        } else {
            exception.addSuppressed(e);
        }
    }

    public ParallelMapperImpl(int threads) {
        this.taskQueue = new TasksQueue();
        Runnable task = () -> {
            try {
                while (!Thread.interrupted()) {
                    taskQueue.getTask().run();
                }
            } catch (InterruptedException e) {
                except(e);
            }
        };
        threadsRunner = new ThreadsRunner(threads, task);
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        MappedList<R> resultList = new MappedList<>(list.size());
        if (closed) {
            throw new InterruptedException("Map was closed.");
        }

        for (int i = 0; i < list.size(); ++i) {
            final int index = i;
            if (!closed) {
                taskQueue.add(() -> resultList.set(index, function.apply(list.get(index))));
            }
        }
        List<R> result = resultList.getMappedList();
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    @Override
    public void close() {
        closed = true;
        threadsRunner.interrupt();
    }

    private static class TasksQueue {
        private final Deque<Runnable> tasks;

        TasksQueue() {
            tasks = new ArrayDeque<>();
        }

        public synchronized void add(Runnable task) {
            tasks.addLast(task);
            notify();
        }

        public synchronized Runnable getTask() throws InterruptedException {
            while (tasks.isEmpty()) {
                wait();
            }
            return tasks.pollFirst();
        }
        public synchronized boolean isEmpty() {
            return tasks.isEmpty();
        }
    }

    private static class MappedList<R> {
        private final List<R> list;
        int inProgress;

        public MappedList(int size) {
            this.list = new ArrayList<>(Collections.nCopies(size, null));
            inProgress = size;
        }

        public synchronized void set(int index, R el) {
            list.set(index, el);
            inProgress--;
            if (inProgress == 0) {
                notify();
            }
        }

        public synchronized List<R> getMappedList() throws InterruptedException {
            while (inProgress != 0) {
                wait();
            }
            return list;
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.mapper advanced info.kgeorgiy.ja.bozhe.concurrent.IterativeParallelism