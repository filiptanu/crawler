package rocks.filip.crawler;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

public class Crawler extends Thread {

    private Source source;
    private Set<String> toCrawl;
    private Set<String> crawled;
    private Set<String> resultUrls;
    private BlockingQueue<String> resultUrlsQueue;
    private boolean finished;
    private Semaphore semaphore;
    private Thread resultProcessor;
    private Map<String, String> cookies;

    public Crawler(Source source, ResultRepository resultRepository) throws IOException {
        this.source = source;
        toCrawl = new HashSet<String>();
        crawled = new HashSet<String>();
        resultUrls = new HashSet<String>();
        resultUrlsQueue = new LinkedBlockingDeque<String>();
        finished = false;
        semaphore = new Semaphore(0);
        resultProcessor = new ResultProcessor(this, resultRepository, semaphore);

        toCrawl.add(source.getSeed());

        cookies = getResponse(source.getSeed(), null).execute().cookies();
    }

    private Connection getResponse(String url, Map<String, String> cookies) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36")
                .referrer("http://www.google.com")
                .followRedirects(true);

        if (cookies != null) {
            connection.cookies(cookies);
        }

        return connection;
    }

    public void run() {
        resultProcessor.start();

        while (!toCrawl.isEmpty()) {
            String url = toCrawl.iterator().next();

            System.out.println("Crawling: " + url);

            if (!crawled.contains(url)) {
                try {
                    Document document = getResponse(url, cookies).execute().parse();

                    for (String cssQuery : source.getToFollowUrlCssQueries()) {
                        Elements toFollow = document.select(cssQuery);

                        for (Element element : toFollow) {
                            String urlToFollow = element.attr("href");

                            if (source.hasRelativeUrls()) {
                                urlToFollow = source.getSeed() + urlToFollow;
                            }

                            if (!crawled.contains(urlToFollow)) {
                                toCrawl.add(urlToFollow);
                            }
                        }
                    }

                    for (String cssQuery : source.getResultPageCssQueries()) {
                        Elements results = document.select(cssQuery);

                        for (Element element : results) {
                            String resultUrl = element.attr("href");

                            if (source.hasRelativeUrls()) {
                                resultUrl = source.getSeed() + resultUrl;
                            }

                            if (!resultUrls.contains(resultUrl)) {
                                resultUrls.add(resultUrl);

                                try {
                                    resultUrlsQueue.put(resultUrl);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Malformed URL: " + url);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                toCrawl.remove(url);
                crawled.add(url);
            }
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        finished = true;
        System.out.println("Finished crawling " + source.getName());
    }

    public Source getSource() {
        return source;
    }

    public BlockingQueue<String> getResultUrlsQueue() {
        return resultUrlsQueue;
    }

    public boolean isFinished() {
        return finished;
    }

}