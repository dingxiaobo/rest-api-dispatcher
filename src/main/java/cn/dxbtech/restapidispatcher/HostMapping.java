package cn.dxbtech.restapidispatcher;

public class HostMapping {
    private String name;
    private String url;
    private String prefix;
    private boolean debug = false;

    public HostMapping() {
    }

    public HostMapping(String name, String url, String prefix) {
        this.name = name;
        this.url = url;
        this.prefix = prefix;
    }

    public HostMapping(String name, String url, String prefix, boolean debug) {
        this.name = name;
        this.url = url;
        this.prefix = prefix;
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "HostMapping{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", prefix='" + prefix + '\'' +
                '}';
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
