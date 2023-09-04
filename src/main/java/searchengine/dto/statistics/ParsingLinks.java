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
@RequiredArgsConstructor
public class ParsingLinks extends RecursiveAction {
    private final SiteForIndexing site;
    private final String url;
    private final int siteDepth;
    private final static int MAX_DEPTH = 1;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sites;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private volatile boolean interrupted = false;
    private boolean isCompleted = false;
    @Getter
    private static List<ParsingLinks> parsingTasks = new ArrayList<>();
    private static String regexForUrl = "(?:https?://)?(?:www\\.)?([a-zA-Z0-9-]+\\.[a-zA-Z]+)(?:/[^\\s]*)?";

    @Override
    protected void compute() {
        parsingTasks.add(this);
        if (siteDepth > MAX_DEPTH) {
            return;
        }
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
        SearchLemmas searchLemmas = new SearchLemmas(pageRepository, lemmaRepository, searchIndexRepository);
        try {
            boolean updatePath = false;
            connectingAndIndexingSite(siteForIndexing, searchLemmas, updatePath);
        } catch (IOException | InterruptedException exception) {
            exceptionHandling(exception, siteForIndexing);
        }
    }

    public boolean checkLink(String link) {
        String path = StartIndexingServiceImpl.addSlashToEnd(site.getUrl());
        return link.matches(regexForUrl)
                && (link.startsWith(path) || link.startsWith(setUrlWithoutDomain(path)))
                && !link.contains("#")
                && !link.contains(".pdf");
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

    public synchronized boolean checkContainsPathInRepository(String path, SiteForIndexing siteForIndexing) {
        Page page = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        return page != null;
    }


    public void saveSiteInStatusFailed(Exception e, Site site) {
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
        siteForIndexing.setName(site.getName());
        siteForIndexing.setUrl(site.getUrl());
        siteForIndexing.setSiteStatus(SiteStatus.FAILED);
        siteForIndexing.setLastError(e.getMessage());
        siteRepository.save(siteForIndexing);
    }

    public synchronized boolean savePageInRepository(String path, OnePathInfo info) {
        String urlWithoutMainPath = urlWithoutMainPath(path);
        if (checkContainsPathInRepository(urlWithoutMainPath, info.getSiteForIndexing())) {
            return false;
        }
        String html = info.getHtml();
        if (html == null) {
            html = "";
        }
        Page page = new Page();
        page.setPath(urlWithoutMainPath);
        page.setCode(info.getStatusCode());
        page.setSite(info.getSiteForIndexing());
        page.setContent(html);
        pageRepository.save(page);
        return true;
    }

    public void interruptTask() {
        interrupted = true;
    }

    public String urlWithoutMainPath(String path) {
        try {
            URL url = new URL(path);
            return url.getPath();
        } catch (Exception e) {
            e.printStackTrace();
            return path;
        }
    }

    public void connectingAndIndexingSite(SiteForIndexing siteForIndexing, SearchLemmas searchLemmas, boolean updatePath) throws IOException, InterruptedException {
        Thread.sleep(150);
        if (interrupted) {
            return;
        }
        Connection.Response response = Jsoup.connect(url).execute();
        Document document = response.parse();
        String html = document.html();
        int statusCode = response.statusCode();
        Elements elements = document.select("a[href]");
        for (Element element : elements) {
            if (interrupted) {
                return;
            }
            OnePathInfo info = new OnePathInfo(siteForIndexing, statusCode, searchLemmas, html);
            indexingOnePath(element, info, updatePath);
        }
    }

    public void exceptionHandling(Exception e, SiteForIndexing siteForIndexing) {
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
            String html = null;
            OnePathInfo info = new OnePathInfo(siteForIndexing, statusCode, html);
            savePageInRepository(url, info);
        }
    }


    public void indexingOnePath(Element element, OnePathInfo info, boolean updatePath) {
        String path = element.absUrl("href");
        if (!checkLink(path)) {
            return;
        }
        int statusCode = info.getStatusCode();
        SiteForIndexing siteForIndexing = info.getSiteForIndexing();
        if (savePageInRepository(path, info) && statusCode == HttpStatus.OK.value()) {
            info.getSearchLemmas().saveLemma(urlWithoutMainPath(path), siteForIndexing);
            if (siteForIndexing.getSiteStatus() != SiteStatus.FAILED) {
                siteForIndexing.setStatusTime(LocalDateTime.now());
            }
            siteRepository.save(siteForIndexing);
        }
        if (interrupted) {
            return;
        }
        if (updatePath) {
            return;
        }
        ParsingLinks task = new ParsingLinks(site, path, siteDepth + 1, pageRepository,
                siteRepository, sites, lemmaRepository, searchIndexRepository);
        task.fork();
        task.join();
    }
}

