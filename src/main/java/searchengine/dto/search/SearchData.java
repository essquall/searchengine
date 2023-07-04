package searchengine.dto.search;


import lombok.Data;

@Data
public class SearchData {
    private boolean result;
    private String error;
    private int count;
    private DetailedSearchItems detailed;
}
