---
title: swagger2整合springmvc快速生成rest风格接口文档
date: 2016-08-06 21:04:21
tags: spring
typora-root-url: ..
---

> Swagger可以用来快速生成RESTful API文档,使后台开发人员与移动端开发人员更好的对接.

<!--more-->

##### Maven dependency
```xml
        <dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-swagger2</artifactId>
            <version>2.5.0</version>
        </dependency>
        <dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-swagger-ui</artifactId>
            <version>2.5.0</version>
        </dependency>

```

##### 配置Swagger
```java
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2).forCodeGeneration(true).select().apis(RequestHandlerSelectors.any())
                //过滤生成链接
                .paths(PathSelectors.regex("/swagger/.*")).build().apiInfo(apiInfo());
    }
    //api接口作者相关信息
    private ApiInfo apiInfo() {
        Contact contact = new Contact("章国东", "http://zhangguodong.me", "zgdgude@gmail.com");
        ApiInfo apiInfo = new ApiInfoBuilder().license("Apache License Version 2.0").title("xxx系统").description("接口文档").contact(contact).version("1.0").build();
        return apiInfo;
    }
}
```
##### 在springmvc的xml文件中加入相关配置

```xml
 <!--<bean class="com.gude.config.SwaggerConfig" /> 使用bean申明可以去掉@configuration-->
 <!--扫描@configuration注解-->
<context:component-scan base-package="com.gude.config"/>
<!--配置静态资源访问-->
<mvc:resources mapping="swagger-ui.html" location="classpath:/META-INF/resources/"/>
<mvc:resources mapping="/webjars/**" location="classpath:/META-INF/resources/webjars/"/>
```

##### 类接口添加相关说明
```java
@Api(value = "HelloController", description = "Hello控制器")
@Controller
@RequestMapping("/swagger")
public class HelloController {
    @GetMapping("/hello")
    public ModelAndView helloWorld() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("hello");
        return mv;
    }

    @ApiOperation("用户登录")
    @ApiImplicitParams({@ApiImplicitParam(name = "username", value = "用户名", required = true, dataType = "string"), @ApiImplicitParam(name = "password", value = "密码", required = true, dataType = "string")})
    @PostMapping("/login")
    public ModelAndView login(String username, String password) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("success");
        return mv;
    }

    @ApiOperation("json返回测试")
    @ResponseBody
    @GetMapping("/json")
    public Person testJson() {
        Person person = new Person("gude", "111111");
        return person;
    }
}
```

* @ApiImplicitParam:对单个参数进行说明,其中dataType一定为小写
* @Api:标志这个类为Swagger资源
* @ApiOperation:描述了一种操作或通常针对特定的路径的HTTP方法。

>更多注解可以查看[官方说明](https://github.com/swagger-api/swagger-core/wiki/Annotations)

 打开http://localhost:8080/project_name/swagger-ui.html ,`project_name`表示你启动项目的名称,如果你以根目录启动则没有`project_name`,当你看到如下界面就表示配置成功了
 ![](https://gitee.com/zhangguodong/image/raw/master/picgo/swagger.png)

 本次整合[源码](https://github.com/gudegg/swagger.git)

 参考[官方文档](https://springfox.github.io/springfox/docs/snapshot/#getting-started)