package searchengine.service.indexing;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import searchengine.config.SiteConfig;
import searchengine.config.SitesConfig;
import searchengine.dto.OnePathInfo;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.search.Impl.LemmaSearcherImpl;
import searchengine.service.indexing.Impl.StartIndexingServiceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
@RequiredArgsConstructor
public class RecursiveLinkParser extends RecursiveAction {
    private final SiteForIndexing siteForIndexing;
    private final String url;
    private final int siteDepth;
    private final static int MAX_DEPTH = 100;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesConfig sites;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final Set<String> paths;
    private AtomicBoolean interrupted = new AtomicBoolean(false);
    @Getter
    private static List<RecursiveLinkParser> parsingTasks = new ArrayList<>();
    private static String regexForUrl = "(?:https?://)?(?:www\\.)?([a-zA-Z0-9-]+\\.[a-zA-Z]+)(?:/[^\\s]*)?";

    @Override
    protected void compute() {
        parsingTasks.add(this);
        if (siteDepth > MAX_DEPTH) {
            return;
        }
        LemmaSearcherImpl searchLemmas = new LemmaSearcherImpl(pageRepository, lemmaRepository, searchIndexRepository);
        try {
            boolean updatePath = false;
            connectingAndIndexingSite(siteForIndexing, searchLemmas, updatePath);
        } catch (IOException | InterruptedException exception) {
            exceptionHandling(exception, siteForIndexing);
        }
    }

    public boolean checkLink(String link) {
        String path = StartIndexingServiceImpl.addSlashToEnd(siteForIndexing.getUrl());
        return link.matches(regexForUrl)
                && (link.startsWith(path) || link.startsWith(setUrlWithoutDomain(path)))
                && !link.contains("?")
                && !link.contains("&")
                && !link.contains("#")
                && !link.contains(".pdf")
                && !link.contains(".eps")
                && !link.contains(".jpg")
                && !link.contains(".jpeg")
                && !link.contains(".doc")
                && !link.contains(".xls")
                && !link.contains(" .xlsx")
                && !paths.contains(link);
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


    public void saveSiteInStatusFailed(Exception e, SiteConfig site) {
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
        siteForIndexing.setName(site.getName());
        siteForIndexing.setUrl(site.getUrl());
        siteForIndexing.setSiteStatus(SiteStatus.FAILED);
        siteForIndexing.setLastError(e.getMessage());
        siteRepository.save(siteForIndexing);
    }

    public boolean savePageInRepository(String path, OnePathInfo info) {
        String urlWithoutMainPath = urlWithoutMainPath(path);
        String html = info.getHtml();
        if (html == null) {
            html = "";
        }try {
        Page page = Page.builder()
                .path(urlWithoutMainPath)
                .code(info.getStatusCode())
                .site(info.getSiteForIndexing())
                .content(html).
                build();
        pageRepository.save(page);
        paths.add(path);
        return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    public void interruptTask() {
        interrupted.set(true);
    }

    public String urlWithoutMainPath(String path) {
        URL url = null;
        try {
            url = new URL(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return url.getPath();
    }

    public void connectingAndIndexingSite(SiteForIndexing siteForIndexing, LemmaSearcherImpl searchLemmas, boolean updatePath) throws IOException, InterruptedException {
        Thread.sleep(150);
        if (interrupted.get()) {
            return;
        }
        Connection.Response response = Jsoup.connect(url).execute();
        Document document = response.parse();
        String html = document.html();
        int statusCode = response.statusCode();
        Elements elements = document.select("a[href]");
        for (Element element : elements) {
            if (interrupted.get()) {
                return;
            }
            OnePathInfo info = new OnePathInfo(siteForIndexing, statusCode, searchLemmas, html);
            indexingOnePath(element, info, updatePath);
        }
    }

    public void exceptionHandling(Exception e, SiteForIndexing siteForIndexing) {
        List<SiteConfig> sitesFromConfig = sites.getSites();
        Optional<SiteConfig> foundSite = sitesFromConfig.stream().filter(site -> site.getUrl().equals(url)).findFirst();
        if (foundSite.isPresent()) {
            SiteConfig site = foundSite.get();
            saveSiteInStatusFailed(e, site);
            return;
        }
        int statusCode = HttpStatus.NOT_FOUND.value();
        if (e instanceof org.jsoup.HttpStatusException) {
            statusCode = ((org.jsoup.HttpStatusException) e).getStatusCode();
        }
            String html = "";
            OnePathInfo info = new OnePathInfo(siteForIndexing, statusCode, html);
            savePageInRepository(url, info);
    }


    public void indexingOnePath(Element element, OnePathInfo info, boolean updatePath) throws IOException {
           String path = element.absUrl("href");
        if (!checkLink(path)) {
            return;
        }
        int statusCode = info.getStatusCode();
        SiteForIndexing siteForIndexing = info.getSiteForIndexing();
        if (savePageInRepository(path,info) && statusCode == HttpStatus.OK.value()) {
            info.getSearchLemmas().saveLemma(urlWithoutMainPath(path), siteForIndexing);
            if (siteForIndexing.getSiteStatus() != SiteStatus.FAILED) {
                siteForIndexing.setStatusTime(LocalDateTime.now());
            }
            siteRepository.save(siteForIndexing);
        } else {
            return;
        }
        if (interrupted.get()) {
            return;
        }
        if (updatePath) {
            return;
        }
        RecursiveLinkParser task = new RecursiveLinkParser(this.siteForIndexing, path, siteDepth + 1, pageRepository,
                siteRepository, sites, lemmaRepository, searchIndexRepository, paths);
        task.fork();
        task.join();
    }
}

