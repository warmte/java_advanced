package info.kgeorgiy.ja.bozhe.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.Math.min;

@SuppressWarnings("unused")
public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper parallelMapper;

    public IterativeParallelism() {
        parallelMapper = null;
    }

    public IterativeParallelism(ParallelMapper parallelMapper) {
        this.parallelMapper = parallelMapper;
    }

    public ParallelMapper getParallelMapper() {
        return parallelMapper;
    }

    private <R, T> R doParallel(int threads,
                                List<? extends T> list,
                                Function<T, R> mapper,
                                Monoid<R> monoid,
                                Function<List<R>, R> processResult) throws InterruptedException {
        if (list == null || list.isEmpty()) {
            return null;
        }

        Function<List<? extends T>, R> blockProcessor = (List<? extends T> x) -> x.stream().map(mapper).reduce(monoid.getIdentity(), monoid.getOperator());
        int blockSize = list.size() / threads + 1, remains = list.size() % threads;
        List<R> localAns = new ArrayList<>(Collections.nCopies(threads, null));
        List<R> ans;

        List<List<? extends T>> tasksList = new ArrayList<>(threads);
        for (int i = 0, start = 0; i < threads; ++i, start += blockSize) {
            if (i == remains) {
                blockSize--;
            }
            tasksList.add(list.subList(start, min(start + blockSize, list.size())));
        }
        if (parallelMapper != null) {
            ans = parallelMapper.map(blockProcessor, tasksList);
        } else {
            ThreadsRunner threadsRunner = new ThreadsRunner(threads);
            for (int i = 0; i < threads; ++i) {
                final int curThread = i;
                threadsRunner.run(() -> localAns.set(curThread, blockProcessor.apply(tasksList.get(curThread))));
            }
            ans = localAns;
            threadsRunner.join();
        }

        ans.removeIf(Objects::isNull);
        return processResult.apply(ans);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return doParallel(threads,
                list,
                x -> x,
                new Monoid<>(list.get(0), BinaryOperator.minBy(comparator)),
                localAns -> localAns.stream().min(comparator).orElseThrow());
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return doParallel(threads,
                list,
                x -> x,
                new Monoid<>(list.get(0), BinaryOperator.maxBy(comparator)),
                localAns -> localAns.stream().max(comparator).orElse(null));
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return doParallel(threads,
                list,
                predicate::test,
                new Monoid<>(Boolean.TRUE, (x, y) -> x && y),
                localAns -> localAns.stream().allMatch(x -> x));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return doParallel(threads,
                list,
                predicate::test,
                new Monoid<>(Boolean.FALSE, (x, y) -> x || y),
                localAns -> localAns.stream().anyMatch(x -> x));
    }

    @Override
    public String join(int threads, List<?> list) throws InterruptedException {
        return doParallel(threads,
                list,
                Objects::toString,
                new Monoid<>("", (x, y) -> x + y),
                localAns -> String.join("", localAns));
    }

    private <T> List<T> mergeLists(List<T> x, List<T> y) {
        List<T> ans = new ArrayList<>(x);
        ans.addAll(y);
        return ans;
    }

    private <T> List<T> listProcesser(List<List<T>> lists) {
        return lists.stream().reduce(new ArrayList<>(), this::mergeLists);
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return doParallel(threads,
                list,
                x -> new ArrayList<>(predicate.test(x) ? List.of(x) : List.of()),
                new Monoid<List<T>>(new ArrayList<>(), this::mergeLists),
                this::listProcesser
        );
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return doParallel(threads,
                list,
                x -> new ArrayList<>(List.of(function.apply(x))),
                new Monoid<List<U>>(new ArrayList<>(), this::mergeLists),
                this::listProcesser
        );
    }

    @Override
    public <T> T reduce(int threads, List<T> list, Monoid<T> monoid) throws InterruptedException {
        return doParallel(threads,
                list,
                x -> x,
                monoid,
                localAns -> localAns.stream().reduce(monoid.getIdentity(), monoid.getOperator())
        );
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> list, Function<T, R> function, Monoid<R> monoid) throws InterruptedException {
        return reduce(threads, map(threads, list, function), monoid);
    }
}

// java -cp ".;info.kgeorgiy.java.advanced.mapper" -p . -m info.kgeorgiy.java.advanced.concurrent advanced info.kgeorgiy.ja.bozhe.concurrent.IterativeParallelism