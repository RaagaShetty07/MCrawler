import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MonzoCrawler {

    private static final int MAX_SUB_DOMAIN_VISIT = 3;
    private static final int THREAD_POOL_SIZE = 10;
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> linksQueue = new LinkedList<>();
    private String domain;


    public static void main(String[] args) {
        String startingUrl = "https://monzo.com/";
        MonzoCrawler crawler = new MonzoCrawler();
        crawler.crawl(startingUrl, 0);
    }

    private void crawl(String url, int depth) {
        if (depth < MAX_SUB_DOMAIN_VISIT) {
            if (!visitedLinks.contains(url)) {
                visitedLinks.add(url);
                System.out.println("Crawling: " + url);

                try {
                    Document document = getHtmlDocument(url);
                    if (domain == null) {
                        domain = getDomainName(url);
                    }

                    NodeList linksOnPage = document.getElementsByTagName("a");

                    for (int i = 0; i < linksOnPage.getLength(); i++) {
                        Node node = linksOnPage.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            String nextUrl = node.getAttributes().getNamedItem("href").getNodeValue();
                            if (isSameDomain(nextUrl)) {
                                linksQueue.add(nextUrl);
                            }
                        }
                    }

                    ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
                    int currentDepth = depth + 1;

                    while (!linksQueue.isEmpty()) {
                        String nextUrl = linksQueue.poll();
                        executor.execute(() -> crawl(nextUrl, currentDepth));
                    }

                    executor.shutdown();
                    executor.awaitTermination(10, TimeUnit.MINUTES);

                } catch (Exception e) {
                    System.err.println("Error fetching/parsing " + url + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean isSameDomain(String url) throws URISyntaxException {
        String domainName = getDomainName(url);
        return domainName != null && domainName.equals(domain);
    }

    private String getDomainName(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String domain = uri.getHost();
        return (domain != null) ? domain.startsWith("www.") ? domain.substring(4) : domain : null;
    }

    private Document getHtmlDocument(String url) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line);
        }

        reader.close();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(urlObj.openStream());
        } catch (Exception e) {
            throw new IOException("Error parsing document: " + e.getMessage());
        }
    }
}
