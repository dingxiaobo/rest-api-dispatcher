package cn.dxbtech.restapidispatcher;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.*;

public class RestApiDispatcher extends HttpServlet {
    private String tokenKey = "token";
    private String apiPrefix;
    private List<HostMapping> hostMappings;

    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private final LocalRestTemplate template;
    private Map<String, HostMapping> hostMapping;

    public RestApiDispatcher() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(5000);
        template = new LocalRestTemplate(requestFactory);
    }

    public RestApiDispatcher(String tokenKey, String apiPrefix, HostMapping... hostMappings) {
        this();
        this.apiPrefix = apiPrefix;
        this.tokenKey = tokenKey;
        this.hostMappings = new LinkedList<HostMapping>();
        this.hostMappings.addAll(Arrays.asList(hostMappings));
    }

    public RestApiDispatcher(String apiPrefix, HostMapping... hostMappings) {
        this();
        this.apiPrefix = apiPrefix;
        this.hostMappings = new LinkedList<HostMapping>();
        this.hostMappings.addAll(Arrays.asList(hostMappings));
    }

    @Override
    public void init() throws ServletException {
        if (hostMappings == null) {
            //满足该条件 代表需要从web.xml中读取initparam
            apiPrefix = getInitParameter("api-prefix");
            String initParameterTokenKey = getInitParameter("token-key");
            if (!StringUtils.isEmpty(initParameterTokenKey)) {
                tokenKey = initParameterTokenKey;
            }
            try {
                TypeReference<List<HostMapping>> typeRef = new TypeReference<List<HostMapping>>() {
                };
                JsonFactory jsonFactory = new JsonFactory();
                jsonFactory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                ObjectMapper objectMapper = new ObjectMapper(jsonFactory);
                try {
                    String hostsJson = getInitParameter("hosts");
                    if (StringUtils.isEmpty(hostsJson)) {
                        throw new JsonParseException(null, "Servlet init param [hosts] not found.");
                    }
                    hostMappings = objectMapper.readValue(hostsJson, typeRef);
                } catch (JsonParseException e) {
                    //json解析失败 不符合json格式 从配置文件中读取json
                    ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
                    String hostsJsonConfigFileLocation = getInitParameter("hosts-config-location");
                    if (StringUtils.isEmpty(hostsJsonConfigFileLocation)) {
                        //servlet init param中找不到hosts-config-location
                        throw new IOException("Read [hosts] and [hosts-config-location] from servlet init param failed. [hosts-config-location] not found.", e);
                    }
                    for (Resource hostsConfigLocation : resourcePatternResolver.getResources(hostsJsonConfigFileLocation)) {
                        InputStream in = hostsConfigLocation.getInputStream();
                        StringBuilder stringBuilder = new StringBuilder();
                        byte[] b = new byte[4096];
                        for (int n; (n = in.read(b)) != -1; ) {
                            stringBuilder.append(new String(b, 0, n));
                        }
                        List<HostMapping> hms = objectMapper.readValue(stringBuilder.toString(), typeRef);
                        if (hostMappings == null) hostMappings = new LinkedList<HostMapping>();
                        hostMappings.addAll(hms);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        hostMapping = new LinkedHashMap<String, HostMapping>();
        for (HostMapping mapping : hostMappings) {
            if (hostMapping.get(mapping.getPrefix()) != null) {
                throw new ServletException("Duplicated prefix[" + mapping.getPrefix() + "]: [" + mapping + "] has same prefix with [" + hostMapping.get(mapping.getPrefix()) + "] ");
            }
            hostMapping.put(mapping.getPrefix(), mapping);
            logger.info("Api mapping{}: [/{}]\t{} -> {}", mapping.isDebug() ? "(local DEBUG)" : "", mapping.getName(), mapping.getPrefix(), mapping.getUrl());
        }

    }

    private String getUrl(HttpServletRequest request) {
        //从request中解析出前缀
        int count = 0;
        String prefix = null;
        String requestURI = request.getRequestURI();
        for (String s : requestURI.split("/")) {
            if (!StringUtils.isEmpty(s)) {
                count++;
                if (count == 2) {
                    prefix = s;
                    break;
                }
            }
        }
        if (prefix == null) return null;
        //根据前缀获取主机
        HostMapping hostMapping = this.hostMapping.get(prefix);
        if (hostMapping == null) {
            return null;
        }
        if (hostMapping.isDebug()) {
            //请求json
            return requestURI;
        }

        String url = hostMapping.getUrl() + requestURI.substring((request.getContextPath() + apiPrefix + '/' + prefix).length());
        if (!StringUtils.isEmpty(request.getQueryString())) {
            url = url + "?" + request.getQueryString();
        }
        logger.debug("request uri: {} -> {}", requestURI, url);
        return url;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int statusCode;
        String responseBody;
        HttpHeaders responseHeaders;
        //发起请求
        try {
            HttpEntity<String> httpEntity = new HttpEntity<String>(charReader(request), getHttpHeaders(request));
            String url = getUrl(request);
            if (StringUtils.isEmpty(url)) {
                super.service(request, response);
                return;
            }
            ResponseEntity<String> exchange = template.exchange(url, HttpMethod.resolve(request.getMethod()), httpEntity, String.class);
            //正常请求
            statusCode = exchange.getStatusCodeValue();
            responseHeaders = exchange.getHeaders();
            responseBody = exchange.getBody();
        } catch (HttpClientErrorException e) {
            //错误请求
            statusCode = e.getRawStatusCode();
            responseBody = e.getResponseBodyAsString();
            responseHeaders = e.getResponseHeaders();
        }

        // headers
        for (String key : responseHeaders.keySet()) {
            StringBuilder value = new StringBuilder("");
            for (String v : responseHeaders.get(key)) {
                value.append(v).append(',');
            }
            //去除最后的分隔符
            if (!StringUtils.isEmpty(value)) {
                value.deleteCharAt(value.length() - 1);
            }
            String v = value.toString();
            response.setHeader(key, v);
        }
        PrintWriter out = response.getWriter();
        out.write(responseBody);
        response.setStatus(statusCode);
        out.flush();
    }

    private HttpHeaders getHttpHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if ("cookie".equalsIgnoreCase(headerName)) continue;
            StringBuilder value = new StringBuilder("");
            Enumeration<String> requestHeaders = request.getHeaders(headerName);
            while (requestHeaders.hasMoreElements()) {
                value.append(requestHeaders.nextElement()).append(',');
            }
            //去除最后的分隔符
            if (!StringUtils.isEmpty(value)) {
                value.deleteCharAt(value.length() - 1);
            }
            headers.add(headerName, value.toString());
        }

        String token = (String) request.getSession().getAttribute(tokenKey);
        headers.add(tokenKey, StringUtils.isEmpty(token) ? "<empty token>" : token);

        return headers;
    }

    private String charReader(HttpServletRequest request) {
        String str;
        StringBuilder wholeStr = new StringBuilder();
        BufferedReader br = null;
        try {
            br = request.getReader();
            while ((str = br.readLine()) != null) {
                wholeStr.append(str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return wholeStr.toString();
    }

}


