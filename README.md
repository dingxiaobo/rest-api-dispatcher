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
    <version>1.2</version>
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
    <!--default is "token"-->
        <param-name>token-key</param-name>
        <param-value>token</param-value>
    </init-param>
    <init-param>
        <param-name>hosts</param-name>
        <param-value>
            [{"name":"testweb8081","url":"http://localhost:8081","prefix":"1"},{"name":"testweb8082","url":"http://localhost:8082","prefix":"2"}]
        </param-value>
    </init-param>
    <init-param>
    <!--Read from json file-->
    <!--Remove [hosts] tag if you need use [hosts-config-location]-->
        <param-name>hosts-config-location</param-name>
        <param-value>classpath:hosts.json</param-value>
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
               new HostMapping("testweb8081", "http://localhost:8081", "1", true),
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

### 1.2 
- 支持debug模式，在该模式下请求结果从本地获取
- json不存在时，读取txt，方便非json格式的内容读取。并且json/txt支持注释
- 支持用.json文件配置hosts
