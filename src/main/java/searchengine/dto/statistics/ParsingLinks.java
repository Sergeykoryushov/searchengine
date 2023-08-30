package searchengine.dto.statistics;

import lombok.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.StartIndexingServiceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RecursiveAction;

@Data
@NoArgsConstructor
public class ParsingLinks extends RecursiveAction {
    private SiteForIndexing site;
    private String url;
    private int depth;
    private final int MAX_DEPTH = 1;
    @Getter(AccessLevel.PUBLIC)
    private PageRepository pageRepository;
    private SiteRepository siteRepository;
    private LemmaRepository lemmaRepository;
    private SearchIndexRepository searchIndexRepository;
    private SitesList sites;
    private volatile boolean interrupted = false;
    private boolean isCompleted = false;
    @Getter
    private static List<ParsingLinks> parsingTasks = new ArrayList<>();
    private static String regexForUrl = "(?:https?://)?(?:www\\.)?([a-zA-Z0-9-]+\\.[a-zA-Z]+)(?:/[^\\s]*)?";

    public ParsingLinks(SiteForIndexing site, String url, int depth,
                        PageRepository pageRepository,
                        SiteRepository siteRepository,
                        SitesList sites,
                        LemmaRepository lemmaRepository,
                        SearchIndexRepository searchIndexRepository) {
        this.site = site;
        this.url = url;
        this.depth = depth;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.sites = sites;
        this.lemmaRepository = lemmaRepository;
        this.searchIndexRepository = searchIndexRepository;
    }

    @Override
    protected void compute() {
        parsingTasks.add(this);
        if (depth > MAX_DEPTH) {
            return;
        }
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
        SearchLemmas searchLemmas = new SearchLemmas(pageRepository, siteRepository, lemmaRepository, searchIndexRepository);
        try {
            Thread.sleep(150);
            if (interrupted) {
                return;
            }
            Connection.Response response = Jsoup.connect(url).execute();
            Document document = response.parse();
            int statusCode = response.statusCode();
            Elements elements = document.select("a[href]");
            for (Element element : elements) {
                if (interrupted) {
                    return;
                }
                indexingOnePath(element, siteForIndexing, statusCode, searchLemmas);
            }
        } catch (IOException | InterruptedException e) {
            List<Site> siteList = sites.getSites();
            Optional<Site> foundSite = siteList.stream().filter(site -> site.getUrl().equals(url)).findFirst();
            if (foundSite.isPresent()) {
                Site site = foundSite.get();
                saveSiteInStatusFailed(e, site);
                return;
            }
            int statusCode = HttpStatus.NOT_FOUND.value();
            if (e instanceof org.jsoup.HttpStatusException) {
                statusCode = ((org.jsoup.HttpStatusException) e).getStatusCode();
            }
            if (!checkContainsPathInRepository(url, siteForIndexing)) {
                savePageInRepository(statusCode, url, siteForIndexing);
            }
        }
    }

    public boolean checkPath(String link) {
        String mainPath = StartIndexingServiceImpl.addSlashToEnd(site.getUrl());
        return link.matches(regexForUrl)
                && (link.startsWith(mainPath) || link.startsWith(setUrlWithoutDomain(mainPath)))
                && !link.contains("#")
                && !link.contains(".pdf");
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

    public synchronized boolean checkContainsPathInRepository(String link, SiteForIndexing siteForIndexing) {
        Page page = pageRepository.findByPathAndSiteId(link, siteForIndexing.getId());
        return page != null;
    }


    public boolean saveSiteInStatusFailed(Exception e, Site site) {
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
        siteForIndexing.setName(site.getName());
        siteForIndexing.setUrl(site.getUrl());
        siteForIndexing.setSiteStatus(SiteStatus.FAILED);
        siteForIndexing.setLastError(e.getMessage());
        siteRepository.save(siteForIndexing);
        return true;
    }

    public synchronized boolean savePageInRepository(int statusCode, String path, SiteForIndexing siteForIndexing) {
        String urlWithoutMainPath = urlWithoutMainPath(path);
        if(checkContainsPathInRepository(urlWithoutMainPath,siteForIndexing)){
            return false;
        }
        String html= null;
        if(statusCode == HttpStatus.OK.value()){
            html = htmlParser(path);
        }
        if(html == null){
            html = "";
        }
        Page page = new Page();
        page.setPath(urlWithoutMainPath);
        page.setCode(statusCode);
        page.setSite(siteForIndexing);
        page.setContent(html);
        pageRepository.save(page);
        return true;
    }
    public void interruptTask() {
        interrupted = true;
    }

    public String urlWithoutMainPath(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getPath();
        } catch (Exception e) {
            e.printStackTrace();
            return urlString;
        }
    }
    public void indexingOnePath(Element element,SiteForIndexing siteForIndexing, int statusCode, SearchLemmas searchLemmas){
        String path = element.absUrl("href");
        if (!checkPath(path)) {
            return;
        }
            if(savePageInRepository(statusCode, path, site) && statusCode == HttpStatus.OK.value()){
                searchLemmas.saveLemma(urlWithoutMainPath(path),siteForIndexing);
                if(siteForIndexing.getSiteStatus() != SiteStatus.FAILED) {
                    siteForIndexing.setStatusTime(LocalDateTime.now());
                }
                siteRepository.save(siteForIndexing);
            }
            if (interrupted) {
                return;
            }
            ParsingLinks task = new ParsingLinks(site, path, depth + 1, pageRepository,
                    siteRepository, sites,lemmaRepository, searchIndexRepository);
            task.fork();
            task.join();
    }
}

