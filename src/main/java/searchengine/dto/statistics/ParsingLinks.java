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
import searchengine.services.StartIndexingServiceImp;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.concurrent.RecursiveAction;

import static org.jsoup.nodes.Document.OutputSettings.Syntax.html;

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
            try {
                Thread.sleep(150);
                Connection connection = Jsoup.connect(url)
                        .ignoreContentType(true);
                Document document = connection.get();
                Elements elements = document.select("a[href]");


                for (Element element : elements) {
                    String link = element.absUrl("href");
                    if (checkLink(link)) {
                        int statusCode = HttpStatus.OK.value();
                        saveInPageRepository(statusCode);
                        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
                        siteForIndexing.setStatusTime(LocalDateTime.now());
                        siteRepository.save(siteForIndexing);
                        ParsingLinks task = new ParsingLinks(site,link, depth + 1,pageRepository,siteRepository);
                        task.fork();
                        task.join();
                    }
                }

            } catch (IOException | InterruptedException e) {
                int statusCode = -1;
                if (e instanceof org.jsoup.HttpStatusException) {
                    statusCode = ((org.jsoup.HttpStatusException) e).getStatusCode();
                }
                saveInSiteRepository(e);
                saveInPageRepository(statusCode);
            }
        }
    }

    public boolean checkLink(String link) {
        return link.matches(regexForUrl) && (link.startsWith(url) || link.startsWith(setUrlWithoutDomain(url))) &&
                !link.contains("#") && checkContainsLinkInRepository(link) && !link.contains(".pdf");
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
    public boolean checkContainsLinkInRepository(String link) {
        Page page = pageRepository.findByPath(link);
        if (page != null) {
            return false;
        } else {
            return true;
        }
    }

    public void saveInSiteRepository(Exception e){
        SiteForIndexing siteForIndexing = new SiteForIndexing();
        siteForIndexing.setName(site.getName());
        siteForIndexing.setUrl(site.getUrl());
        siteForIndexing.setSiteStatus(SiteStatus.FAILED);
        siteForIndexing.setLastError(e.getMessage());
        siteRepository.save(siteForIndexing);
    }
    public void saveInPageRepository(int statusCode){
        if(statusCode == -1){
            statusCode = HttpStatus.NOT_FOUND.value();
        }
        Page page = new Page();
        String html = htmlParser(url);
        page.setPath(url);
        page.setCode(statusCode);
        page.setSite(site);
        page.setContent(html);
        pageRepository.save(page);
    }
}

