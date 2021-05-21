package info.kgeorgiy.ja.bozhe.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class WebCrawler implements AdvancedCrawler {
    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadService;
    private final ExecutorService extractService;
    private final ConcurrentHashMap<String, Queue<String>> hostQueues;
    private final ConcurrentHashMap<String, Integer> busyProcesses;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloadService = Executors.newFixedThreadPool(downloaders);
        this.extractService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.hostQueues = new ConcurrentHashMap<>();
        this.busyProcesses = new ConcurrentHashMap<>();
    }

    @Override
    public Result download(String url, int maxDepth, List<String> hosts) {
        return downloadFromRoot(url, maxDepth, hosts::contains);
    }

    @Override
    public Result download(String url, int maxDepth) {
        return downloadFromRoot(url, maxDepth, x -> true);
    }

    private Result downloadFromRoot(String url, int maxDepth, Predicate<String> accept) {
        Phaser phaser = new Phaser(1);
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> urlsToProcess = ConcurrentHashMap.newKeySet();
        Map<String, IOException> errorsMap = new ConcurrentHashMap<>();
        urlsToProcess.add(url);
        int depth = 0;
        while (depth < maxDepth && !urlsToProcess.isEmpty()) {
            depth++;
            final boolean finalLayer = (depth == maxDepth);
            List<String> layer = List.copyOf(urlsToProcess).stream().distinct()
                    .filter((x) -> !processed.contains(x))
                    .collect(Collectors.toList());
            urlsToProcess.clear();
            layer.forEach(curUrl -> processUrl(curUrl, finalLayer, phaser, processed, errorsMap, urlsToProcess, accept));
            phaser.arriveAndAwaitAdvance();
        }
        processed.removeAll(errorsMap.keySet());
        return new Result(List.copyOf(processed), errorsMap);
    }

    private void processUrl(String url,
                            boolean finalLayer,
                            Phaser phaser,
                            Set<String> processed,
                            Map<String, IOException> errorsMap,
                            Set<String> urlsToProcess,
                            Predicate<String> accept) {
        phaser.register();
        downloadService.submit(() -> {
            try {
                String host = URLUtils.getHost(url);
                if (accept.test(host)) {
                    processed.add(url);
                    busyProcesses.putIfAbsent(host, 0);
                    hostQueues.putIfAbsent(host, new ConcurrentLinkedQueue<>());
                    if (busyProcesses.get(host) >= perHost) {
                        hostQueues.get(host).add(url);
                    } else {
                        busyProcesses.put(host, busyProcesses.get(host) + 1);
                        downloadDoc(url, host, finalLayer, phaser, errorsMap, urlsToProcess);
                    }
                } else {
                    phaser.arriveAndDeregister();
                }
            } catch (MalformedURLException e) {
                errorsMap.put(url, e);
            }
        });

    }

    private void downloadDoc(String url,
                             String host,
                             boolean finalLayer,
                             Phaser phaser,
                             Map<String, IOException> errorsMap,
                             Set<String> urlsToProcess) {
        try {
            Document doc = downloader.download(url);
            if (!finalLayer) {
                processDoc(doc, phaser, urlsToProcess);
            }
        } catch (IOException e) {
            errorsMap.put(url, e);
        } finally {
            phaser.arriveAndDeregister();
            if (!hostQueues.get(host).isEmpty()) {
                String curUrl = hostQueues.get(host).poll();
                downloadDoc(curUrl, host, finalLayer, phaser, errorsMap, urlsToProcess);
            } else {
                busyProcesses.put(host, busyProcesses.get(host) - 1);
            }
        }
    }

    private void processDoc(Document doc, Phaser phaser, Set<String> urlsToProcess) {
        phaser.register();
        extractService.submit(() -> {
            try {
                urlsToProcess.addAll(doc.extractLinks());
            } catch (IOException ignored) {}
            finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || args[0] == null) {
            System.err.println("Incorrect URL string.");
            return;
        }
        final String url = args[0];
        final int DEFAULT = 16;
        final int depth = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT;
        final int downloaders = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT;
        final int exctractors = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT;
        final int perHost = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT;
        try (WebCrawler webCrawler = new WebCrawler(new CachingDownloader(), downloaders, exctractors, perHost)) {
            webCrawler.download(url, depth);
        } catch (IOException e) {
            System.err.println("CachingDownloader isn't working properly: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void close() {
        extractService.shutdown();
        downloadService.shutdown();
        try {
            boolean ex = extractService.awaitTermination(1, TimeUnit.SECONDS);
            boolean dwn = downloadService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Termination was interrupted: " + e.getMessage());
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.crawler hard info.kgeorgiy.ja.bozhe.crawler.WebCrawler