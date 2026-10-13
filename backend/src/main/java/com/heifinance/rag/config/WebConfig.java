package com.heifinance.rag.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * Serves the built Vite SPA from the configured locations and forwards client-side routes
 * (for example {@code /c/<conversation-id>}) to {@code index.html}.
 *
 * <p>REST endpoints under {@code /api/**} are handled by controller mappings, which always take
 * precedence over these resource handlers.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ResourceLoader resourceLoader;
    private final String[] staticLocations;
    private final String[] allowedOriginPatterns;

    public WebConfig(ResourceLoader resourceLoader,
                     @Value("${heifinance.web.static-locations:classpath:/static/,file:../frontend/dist/,file:./frontend/dist/}")
                     String[] staticLocations,
                     @Value("${heifinance.web.allowed-origin-patterns:*}")
                     String[] allowedOriginPatterns) {
        this.resourceLoader = resourceLoader;
        this.staticLocations = staticLocations;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // The packaged app serves its own SPA, and a browser still sends an Origin header on a
        // same-origin POST. An allow-list containing only the Vite dev origins therefore rejects
        // every message sent from the built app with 403 "Invalid CORS request", so the default
        // accepts any origin. The API carries no cookies or credentials, so CORS is not a security
        // boundary here; set HEIFINANCE_ALLOWED_ORIGIN_PATTERNS to lock it down behind a gateway.
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(staticLocations)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver(resolveIndexCandidates()));
    }

    private List<Resource> resolveIndexCandidates() {
        List<Resource> candidates = new ArrayList<>();
        for (String location : staticLocations) {
            String base = location.endsWith("/") ? location : location + "/";
            candidates.add(resourceLoader.getResource(base + "index.html"));
        }
        return candidates;
    }

    /**
     * Returns the requested static asset when it exists, otherwise falls back to the SPA entry
     * point so that client-side routes survive a full page load.
     */
    static final class SpaFallbackResolver extends PathResourceResolver {

        private final List<Resource> indexCandidates;

        SpaFallbackResolver(List<Resource> indexCandidates) {
            this.indexCandidates = indexCandidates;
        }

        @Override
        protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
                                                   List<? extends Resource> locations,
                                                   ResourceResolverChain chain) {
            for (Resource location : locations) {
                try {
                    Resource requested = location.createRelative(requestPath);
                    if (requested.exists() && requested.isReadable()) {
                        return requested;
                    }
                } catch (IOException ex) {
                    // Unreadable location: fall through and probe the next one.
                }
            }

            // Never swallow API traffic, and never mask a genuinely missing asset with the SPA shell.
            if (requestPath.startsWith("api/") || requestPath.startsWith("actuator/")
                    || requestPath.contains(".")) {
                return null;
            }

            for (Resource candidate : indexCandidates) {
                if (candidate.exists() && candidate.isReadable()) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
