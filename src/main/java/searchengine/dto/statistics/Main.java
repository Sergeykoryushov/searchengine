package searchengine.dto.statistics;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.services.StartIndexingServiceImp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static String text = "\n" +
            "\n" +
            "<html>\n" +
            "    <head><title>Access Blocked</title></head>\n" +
            "    <body>\n" +
            "        Access to resource was blocked. <br>\n" +
            "        Support id: 9387a62c-03f7-45f3-8977-415c890e4dab\n" +
            "    </body>\n" +
            "</html>\n" +
            "    ";
    public static void main(String[] args) {
     SearchLemmas searchLemmas = new SearchLemmas();
        HashMap<String, Integer> lemmasCountMap = searchLemmas.gettingLemmasInText(text);
        System.out.println(lemmasCountMap.size());
        for (Map.Entry<String, Integer> entry : lemmasCountMap.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
    }
}
