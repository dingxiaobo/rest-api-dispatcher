package cn.dxbtech.restapidispatcher;

import java.util.Arrays;
import java.util.List;

public class HostMapping {
    private String name;
    private String url;
    private List<String> prefixes;

    public HostMapping() {
    }

    public HostMapping(String name, String url, String... prefixes) {
        this.name = name;
        this.url = url;
        this.prefixes = Arrays.asList(prefixes);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getPrefixes() {
        return prefixes;
    }

    public void setPrefixes(List<String> prefixes) {
        this.prefixes = prefixes;
    }
}
