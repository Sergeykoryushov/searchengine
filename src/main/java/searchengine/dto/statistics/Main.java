//package searchengine.dto.statistics;
//
//import org.apache.lucene.morphology.LuceneMorphology;
//import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import searchengine.services.StartIndexingServiceImp;
//
//import java.io.IOException;
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.util.HashMap;
//import java.util.List;
//
//public class Main {
////    private static String text = "<p>This is <b>bold</b> text.</p>";
//    public static void main(String[] args) {
//        String fullUrl = "https://www.hostname.com/level1/level2/page.html";
//
//        String baseUrl = extractBaseUrl(fullUrl);
//        System.out.println("Base URL: " + baseUrl);
//    }
//    public static String extractBaseUrl(String fullUrl) {
//        try {
//            URI uri = new URI(fullUrl);
//            String scheme = uri.getScheme();
//            String host = uri.getHost();
//            return scheme + "://" + host + "/";
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//}
