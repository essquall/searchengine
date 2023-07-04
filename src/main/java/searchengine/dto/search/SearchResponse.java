package searchengine.dto.search;

import lombok.Data;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;

import java.util.List;

@Data
public class SearchResponse {
    List<SearchData> data;
}
