package searchengine.dto.statistics;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.services.StartIndexingServiceImp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
        List<Integer> list1 = new ArrayList<>();
        list1.add(1);
        list1.add(3);
        list1.add(4);


        List<Integer> list2 = new ArrayList<>();
        list2.add(7);
        list2.add(9);
        list2.add(5);
        list2.add(8);
        list2.add(2);

        // Создаем Set для хранения уникальных значений
        Set<Integer> uniqueValues = new HashSet<>();

        // Добавляем значения из первого списка в Set
        uniqueValues.addAll(list1);

        // Оставляем только значения, которые есть во втором списке
        list1.retainAll(list2);

        // Создаем новый список с одинаковыми значениями
        List<Integer> result = new ArrayList<>(uniqueValues);

        System.out.println(list1); // Вывод: [2, 5]
        }
    }

