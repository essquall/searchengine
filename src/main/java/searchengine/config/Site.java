package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
public class Site {
    //    @Value("{indexing-settings.sites.url}")
    private String url;
    //    @Value("{indexing-settings.sites.name}")
    private String name;
}
