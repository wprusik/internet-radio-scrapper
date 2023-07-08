package com.github.wprusik.radioscrapper;

import com.github.wprusik.radioscrapper.model.MenuCategory;
import com.github.wprusik.radioscrapper.model.RadioCategory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.htmlunit.html.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
class MenuExtractor {

    private final WebClient webClient;
    private final String baseUrl;
    private final RadioCategoryExtractor radioCategoryExtractor;
    private final @Nullable StorageService storageService;

    public MenuExtractor(WebClient webClient, String baseUrl, String baseDirectory, int delayBetweenDownloads) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.radioCategoryExtractor = new RadioCategoryExtractor(webClient, baseUrl, delayBetweenDownloads);
        this.storageService = baseDirectory != null ? new StorageService(baseDirectory) : null;
    }

    @SneakyThrows
    List<MenuCategory> getAllCategories() {
        HtmlPage page = webClient.getPage(baseUrl);
        HtmlUnorderedList ulMenu = (HtmlUnorderedList) page.getBody().getElementsByAttribute("ul", "class", "nav navbar-nav").get(0);
        return extractListItems(ulMenu).stream()
                .map(this::fetchMenuCategory)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    MenuCategory getListenCategory() {
        return findCategory("Listen");
    }

    @SneakyThrows
    @SuppressWarnings("SameParameterValue")
    private MenuCategory findCategory(String categoryName) {
        HtmlPage page = webClient.getPage(baseUrl);
        HtmlUnorderedList ulMenu = (HtmlUnorderedList) page.getBody().getElementsByAttribute("ul", "class", "nav navbar-nav").get(0);
        return extractListItems(ulMenu).stream()
                .filter(ul -> extractCategoryName(ul).equalsIgnoreCase(categoryName))
                .map(this::fetchMenuCategory)
                .findAny().orElseThrow();
    }

    private MenuCategory fetchMenuCategory(HtmlListItem category) {
        String categoryName = null;
        Map<String, String> links = null;
        for (DomElement el : category.getChildElements()) {
            if (el instanceof HtmlAnchor) {
                categoryName = el.getTextContent().replace("\t", "").replace("\n", "").trim();
            } else if (el instanceof HtmlUnorderedList) {
                links = extractLinks((HtmlUnorderedList) el);
            }
            if (categoryName != null && links != null) {
                return buildMenuCategory(categoryName, links);
            }
        }
        throw new IllegalStateException("Unable to find required elements to build menu category");
    }

    private String extractCategoryName(HtmlListItem category) {
        for (DomElement el : category.getChildElements()) {
            if (el instanceof HtmlAnchor) {
                String categoryName = el.getTextContent().replace("\t", "").replace("\n", "").trim();
                if (StringUtils.isNotBlank(categoryName)) {
                    return categoryName;
                }
            }
        }
        throw new IllegalStateException("Unable to find non-empty category name");
    }

    private MenuCategory buildMenuCategory(String categoryName, Map<String, String> subcategoryLinks) {
        List<RadioCategory> subcategories = storageService != null ? storageService.load() : new ArrayList<>();
        if ("Listen".equalsIgnoreCase(categoryName)) {
            for (Map.Entry<String, String> entry : subcategoryLinks.entrySet()) {
                RadioCategory category = findSubcategoryByName(subcategories, entry.getKey())
                        .orElseGet(() -> radioCategoryExtractor.getRadioCategory(entry.getKey(), entry.getValue()));

                if (storageService != null) {
                    category = storageService.storePlaylists(category);
                    subcategories.add(category);
                    storageService.save(subcategories);
                } else {
                    subcategories.add(category);
                }
            }
        }
        return new MenuCategory(categoryName, subcategories);
    }

    private Optional<RadioCategory> findSubcategoryByName(List<RadioCategory> categories, String name) {
        return categories.stream().filter(c -> name.equalsIgnoreCase(c.name())).findAny();
    }

    private Map<String, String> extractLinks(HtmlUnorderedList ul) {
        return extractListItems(ul).stream()
                .map(this::extractAnchor)
                .filter(Objects::nonNull)
                .filter(d -> !d.getTextContent().toLowerCase().contains("more genres"))
                .collect(Collectors.toMap(DomNode::getTextContent, a -> a.getAttribute("href")));
    }

    private @Nullable HtmlAnchor extractAnchor(HtmlListItem item) {
        return StreamSupport.stream(item.getChildElements().spliterator(), false)
                .filter(el -> el instanceof HtmlAnchor)
                .map(HtmlAnchor.class::cast)
                .findAny().orElse(null);
    }

    private List<HtmlListItem> extractListItems(HtmlUnorderedList ul) {
        List<HtmlListItem> result = new ArrayList<>();
        for (DomElement el : ul.getChildElements()) {
            if (el instanceof HtmlListItem) {
                result.add((HtmlListItem) el);
            }
        }
        return result;
    }
}