package dev.filiptanu.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ResultProcessor extends Thread {

    private Crawler crawler;
    private ResultRepository resultRepository;

    public ResultProcessor(Crawler crawler, ResultRepository resultRepository) {
        this.crawler = crawler;
        this.resultRepository = resultRepository;
    }

    public void run() {
        while (!crawler.isFinished()) {
            try {
                String url = crawler.getResultUrlsQueue().poll(5, TimeUnit.SECONDS);

                if (url != null) {
                    Map<String, ResultValueEntity> resultValueCssQueries = crawler.getSource().getResultValueEntities();

                    Map<String, String> results = new HashMap<>();
                    results.put("url", url);
                    results.put("source", crawler.getSource().getName());

                    Document document = Jsoup.connect(url).get();
                    resultValueCssQueries.forEach((resultKey, resultValueEntity) -> {
                        String resultValue = resultValueEntity.getResultType().extractResult(document, resultValueEntity.getResultValueCssQuery());

                        Function<String, String> resultValueCleanupStrategy = crawler.getSource().getResultValueCleanupStrategies().get(resultKey);

                        if (resultValueCleanupStrategy != null) {
                            resultValue = resultValueCleanupStrategy.apply(resultValue);
                        }

                        results.put(resultKey, resultValue);
                    });

                    resultRepository.saveResults(results);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("ResultProcessor finished");
    }

}