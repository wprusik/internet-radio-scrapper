package com.github.wprusik.radioscrapper;

import com.github.wprusik.radioscrapper.model.RadioCategory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
class BaseExtractor {

    private final WebClient webClient;
    private final String baseUrl;
    private final @Nullable StorageService storageService;

    public BaseExtractor(WebClient webClient, String baseUrl, String baseDirectory) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.storageService = baseDirectory != null ? new StorageService(baseDirectory) : null;
    }

    @SneakyThrows
    List<RadioCategory> getAllRadioCategories() {
        HtmlPage page = webClient.getPage(baseUrl + "/stations/");
        Map<String, String> categoryLinks = page.getBody().getElementsByAttribute("dt", "class", "text-capitalize").stream()
                .map(this::extractAnchor)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DomNode::getTextContent, e -> e.getAttribute("href")));
        return fetchRadioCategories(categoryLinks);
    }

    private List<RadioCategory> fetchRadioCategories(Map<String, String> categoryLinks) {
        List<RadioCategory> categories = storageService != null ? storageService.load() : new ArrayList<>();
        List<String> genres = categoryLinks.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        RadioCategoryExtractor radioCategoryExtractor = new RadioCategoryExtractor(webClient, baseUrl, genres);
        log.debug("Loaded radio categories: {}/{}", categories.size(), genres.size());

        for (Map.Entry<String, String> entry : categoryLinks.entrySet()) {
            if (isMissing(categories, entry.getKey())) {
                log.debug("Retrieving radio category {}/{}: {}", (categories.size() + 1), genres.size(), entry.getKey());
                RadioCategory category = fetchRadioCategory(radioCategoryExtractor, entry.getKey(), entry.getValue());
                categories.add(category);
                store(categories);
            }
        }
        return categories;
    }

    private RadioCategory fetchRadioCategory(RadioCategoryExtractor radioCategoryExtractor, String name, String link) {
        RadioCategory category = radioCategoryExtractor.getRadioCategory(name, link);
        return storageService != null ? storageService.storePlaylists(category) : category;
    }

    private void store(List<RadioCategory> subcategories) {
        if (storageService != null) {
            storageService.save(subcategories);
        }
    }

    private boolean isMissing(List<RadioCategory> categories, String name) {
        return categories.stream().noneMatch(c -> name.equalsIgnoreCase(c.name()));
    }

    private @Nullable HtmlAnchor extractAnchor(HtmlElement item) {
        return StreamSupport.stream(item.getChildElements().spliterator(), false)
                .filter(el -> el instanceof HtmlAnchor)
                .map(HtmlAnchor.class::cast)
                .findAny().orElse(null);
    }
}
