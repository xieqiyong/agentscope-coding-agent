package com.agentplatform.runtime.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 搜索工具。
 * 中文注释：先提供最小可用能力，后续治理模块再接入供应商配置、配额和审计策略。
 */
public class CodingAgentWebSearchTools {

    private static final Pattern RESULT_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "WebSearch", description = "Search the web and return title/url snippets. Supports allowed_domains and blocked_domains filters.", readOnly = true)
    public String WebSearch(
            @ToolParam(name = "query", description = "Search query.") String query,
            @ToolParam(name = "allowed_domains", required = false, description = "Only include these domains.") List<String> allowedDomains,
            @ToolParam(name = "blocked_domains", required = false, description = "Exclude these domains.") List<String> blockedDomains) {
        if (!StringUtils.hasText(query)) {
            return "query cannot be empty";
        }

        String url = "https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 coding-agent-websearch")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "WebSearch failed: HTTP " + response.statusCode();
            }
            return formatResults(query, response.body(), allowedDomains, blockedDomains);
        } catch (Exception e) {
            return "WebSearch failed: " + e.getMessage();
        }
    }

    private String formatResults(String query, String html, List<String> allowedDomains, List<String> blockedDomains) {
        Matcher matcher = RESULT_PATTERN.matcher(html);
        List<SearchResult> results = new ArrayList<>();
        while (matcher.find() && results.size() < 8) {
            String rawUrl = decodeDuckDuckGoUrl(matcher.group(1));
            String title = stripHtml(matcher.group(2));
            if (!StringUtils.hasText(rawUrl) || !StringUtils.hasText(title)) {
                continue;
            }
            if (!passesDomainFilter(rawUrl, allowedDomains, blockedDomains)) {
                continue;
            }
            results.add(new SearchResult(title, rawUrl));
        }

        if (results.isEmpty()) {
            return "No web search results for: " + query;
        }

        StringBuilder builder = new StringBuilder("WebSearch results for: ").append(query).append("\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(result.title())
                    .append("\n   ")
                    .append(result.url())
                    .append("\n");
        }
        return builder.toString();
    }

    private String decodeDuckDuckGoUrl(String rawUrl) {
        String decoded = htmlDecode(rawUrl);
        int uddgIndex = decoded.indexOf("uddg=");
        if (uddgIndex < 0) {
            return decoded;
        }
        String value = decoded.substring(uddgIndex + 5);
        int nextParam = value.indexOf('&');
        if (nextParam >= 0) {
            value = value.substring(0, nextParam);
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean passesDomainFilter(String url, List<String> allowedDomains, List<String> blockedDomains) {
        String host = host(url);
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if (blockedDomains != null) {
            for (String domain : blockedDomains) {
                if (domainMatches(host, domain)) {
                    return false;
                }
            }
        }
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }
        for (String domain : allowedDomains) {
            if (domainMatches(host, domain)) {
                return true;
            }
        }
        return false;
    }

    private String host(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            return host.toLowerCase().replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean domainMatches(String host, String domain) {
        if (!StringUtils.hasText(domain)) {
            return false;
        }
        String normalized = domain.toLowerCase().replaceFirst("^www\\.", "");
        return host.equals(normalized) || host.endsWith("." + normalized);
    }

    private String stripHtml(String html) {
        return htmlDecode(html.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String htmlDecode(String text) {
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private record SearchResult(String title, String url) {
    }
}
