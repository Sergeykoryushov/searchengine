package searchengine.dto.statistics;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Data
public class ParsingLinks extends RecursiveAction {
    private final SiteForIndexing site;
    private final String url;
    private final int depth;
    private final int MAX_DEPTH = 2;
    @Getter(AccessLevel.PUBLIC)
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;


    private static String regexForUrl = "(?:https?://)?(?:www\\.)?([a-zA-Z0-9-]+\\.[a-zA-Z]+)(?:/[^\\s]*)?";

    @Override
    protected void compute() {
        if (depth <= MAX_DEPTH) {
            SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
            try {
                Thread.sleep(150);
                Connection connection = Jsoup.connect(url).ignoreContentType(true);
                Document document = connection.get();
                Elements elements = document.select("a[href]");
                for (Element element : elements) {
                    String link = element.absUrl("href");
                    if (checkLink(link)) {
                        int statusCode = HttpStatus.OK.value();
                        savePageInRepository(statusCode, link, site);
                        siteForIndexing.setStatusTime(LocalDateTime.now());
                        siteRepository.save(siteForIndexing);
                        ParsingLinks task = new ParsingLinks(site, link, depth + 1, pageRepository, siteRepository);
                        task.fork();
                        task.join();
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (siteForIndexing.getUrl().equals(url)) {
                    saveSiteInRepository(e);
                }
                int statusCode = -1;
                if (e instanceof org.jsoup.HttpStatusException) {
                    statusCode = ((org.jsoup.HttpStatusException) e).getStatusCode();
                }
                savePageInRepository(statusCode, url, siteForIndexing);
            }
        }
    }

    public boolean checkLink(String link) {
        return link.matches(regexForUrl) && (link.startsWith(url) || link.startsWith(setUrlWithoutDomain(url))) &&
                !link.contains("#") && !link.contains(".pdf");
    }

    public String htmlParser(String url) {
        String html = null;
        try {
            Document doc = Jsoup.connect(url).get();
            html = doc.html();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return html;
    }

    public String setUrlWithoutDomain(String siteUrl) {
        String targetUrl = null;
        try {
            URL url = new URL(siteUrl);
            String domain = url.getHost();
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }
            targetUrl = url.getProtocol() + "://" + domain;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return targetUrl;
    }

    public synchronized boolean checkContainsLinkInRepository(String link) {
        Page page = pageRepository.findByPath(link);
        return page != null;
    }


    public void saveSiteInRepository(Exception e) {
        SiteForIndexing siteForIndexing = new SiteForIndexing();
        siteForIndexing.setName(site.getName());
        siteForIndexing.setUrl(site.getUrl());
        siteForIndexing.setSiteStatus(SiteStatus.FAILED);
        siteForIndexing.setLastError(e.getMessage());
        siteRepository.save(siteForIndexing);
    }

    public void savePageInRepository(int statusCode, String link, SiteForIndexing siteForIndexing) {
        if (checkContainsLinkInRepository(link)) {
            return;
        }
        String html= null;
        if(statusCode == HttpStatus.OK.value()){
            html = htmlParser(link);
        }
        if (statusCode == -1) {
            statusCode = HttpStatus.NOT_FOUND.value();
            html = "";
        }
        Page page = new Page();
        page.setPath(link);
        page.setCode(statusCode);
        page.setSite(siteForIndexing);
        page.setContent(html);
        pageRepository.save(page);
    }
}

