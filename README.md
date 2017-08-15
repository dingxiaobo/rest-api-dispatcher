# rest-api-dispatcher
A simple util helps you avoid 'Access-Control-Allow-Origin' problem.

```js
/* get page from localhost:8080 */
// cause problem
$.get('http://localhost:8081/user/1',function (r) {
    console.log(r);
});

// perfect :)
$.get('http://localhost:8080/api/1/user/1',function (r) {
    console.log(r);
});
```

## install
```sh
$ mvn source:jar install
```

## dependency
```xml
<dependency>
    <groupId>cn.dxbtech</groupId>
    <artifactId>rest-api-dispatcher</artifactId>
    <version>1.1</version>
</dependency>
```

## Usage

### option 1
##### web.xml
```xml
<servlet>
    <servlet-name>rest-api-dispatcher</servlet-name>
    <servlet-class>cn.dxbtech.restapidispatcher.RestApiDispatcher</servlet-class>
    <init-param>
        <param-name>api-prefix</param-name>
        <param-value>/api</param-value>
    </init-param>
    <init-param>
        <param-name>token-key</param-name>
        <param-value>token</param-value>
    </init-param>
    <init-param>
        <param-name>hosts</param-name>
        <param-value>
            [{"name":"testweb8081","url":"http://localhost:8081","prefix":"1"},{"name":"testweb8082","url":"http://localhost:8082","prefix":"2"}]
        </param-value>
    </init-param>
    <load-on-startup>1000</load-on-startup>
</servlet>

<servlet-mapping>
    <servlet-name>rest-api-dispatcher</servlet-name>
    <url-pattern>/api/*</url-pattern>
</servlet-mapping>
```

### option 2
##### spring boot
```java
@SpringBootApplication  
public class App{  
      
    public static void main( String[] args ){  
        SpringApplication.run(App.class, args);  
    }  
      
   @Bean
   public ServletRegistrationBean restApiDispatcherServletRegistration() {
       String apiPrefix = "/api";

       ServletRegistrationBean registration = new ServletRegistrationBean(new RestApiDispatcher(apiPrefix,
               new HostMapping("testweb8081", "http://localhost:8081", "1"),
               new HostMapping("testweb8082", "http://localhost:8082", "2")
       ));
       registration.setLoadOnStartup(1000);
       registration.addUrlMappings(apiPrefix + "/*");
       return registration;
   }
}  
```


## Updates

### 1.1
- 唯一前缀标识不同服务