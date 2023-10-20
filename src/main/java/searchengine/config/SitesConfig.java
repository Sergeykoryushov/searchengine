package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesConfig {
    private List<SiteConfig> sites = new CopyOnWriteArrayList<>();

}
