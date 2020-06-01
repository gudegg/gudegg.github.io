---
title: 学习dubbo源码-新版本注解
date: 2018-12-25 20:27:15
tags: [dubbo]
---

# <a name="bthsrq"></a>服务发布与引用



![image.png | left | 747x508](https://gitee.com/zhangguodong/image/raw/master/null/1545740875098-ddee7b85-5216-4338-ae14-746097d42847.png "")


> @Reference实现和@Autowired相似，参考了AutowiredAnnotationBeanPostProcessor

<!--more-->

# <a name="biexst"></a>数据绑定


![image.png | left | 747x756](https://gitee.com/zhangguodong/image/raw/master/null/1545740917019-77d001db-b24c-47cc-905d-fc59bd9c8707.png "")

# <a name="hqyopi"></a>自定义案例
```java
@Configuration
@EnableDubboConfigBinding(prefix = "dubbo.registry", type = RegistryConfig.class)
public class MyDataBind {
}
```
```java
public class MyTest {
    @Test
    public void demo1() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        ConfigurableEnvironment environment = context.getEnvironment();
        environment.getSystemProperties().putIfAbsent("dubbo.registry.address", "zookeeper://127.0.0.1:2181");
        context.register(MyDataBind.class);
        context.refresh();
        RegistryConfig bean = context.getBean(RegistryConfig.class);
        Assert.assertNotNull(bean);
    }
}
```
会将dubbo.registry前缀的参数绑定到RegistryConfig对象，并注入容器
