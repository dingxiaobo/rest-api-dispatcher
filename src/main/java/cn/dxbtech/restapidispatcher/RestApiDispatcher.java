package cn.dxbtech.restapidispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class RestApiDispatcher extends HttpServlet {
    private String tokenKey = "token";
    private String apiPrefix;
    private List<HostMapping> hostMappings;

    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private final RestTemplate template;
    private Map<String, String> hostMapping;

    public RestApiDispatcher() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(5000);
        template = new RestTemplate(requestFactory);
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
                hostMappings = new ObjectMapper().readValue(getInitParameter("hosts"), new TypeReference<List<HostMapping>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        hostMapping = new LinkedHashMap<String, String>();
        for (HostMapping mapping : hostMappings) {
            for (String prefix : mapping.getPrefixes()) {
                hostMapping.put(prefix, mapping.getUrl());
                logger.info("Api mapping: [/{}]\t{} -> {}", mapping.getName(), prefix, mapping.getUrl());
            }
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
        String url = hostMapping.get(prefix) + requestURI.substring((request.getContextPath() + apiPrefix).length());
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
        }
        return wholeStr.toString();
    }

}


