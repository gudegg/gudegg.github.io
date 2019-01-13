---
title: ImportSelector,ImportBeanDefinitionRegistrar,@Import实现自定义注解
date: 2018-12-23 14:02:25
tags: [spring]
---

# <a name="f371yr"></a>@Import
官方解释:
```plain
Indicates one or more @Configuration classes to import.
Provides functionality equivalent to the <import/> element in Spring XML. Allows for importing @Configuration classes, ImportSelector and ImportBeanDefinitionRegistrar implementations, as well as regular component classes (as of 4.2; analogous to AnnotationConfigApplicationContext.register).
@Bean definitions declared in imported @Configuration classes should be accessed by using @Autowired injection. Either the bean itself can be autowired, or the configuration class instance declaring the bean can be autowired. The latter approach allows for explicit, IDE-friendly navigation between @Configuration class methods.
May be declared at the class level or as a meta-annotation.
If XML or other non-@Configuration bean definition resources need to be imported, use the @ImportResource annotation instead.
```
翻译：
```plain
表示要导入的一个或多个@Configuration类。
提供与Spring XML中的<import />元素等效的功能。 允许导入@Configuration类，ImportSelector和ImportBeanDefinitionRegistrar实现，以及常规组件类（从4.2开始;类似于AnnotationConfigApplicationContext.register）。
应该使用@Autowired注入来访问在导入的@Configuration类中声明的@Bean定义。 bean本身可以自动装配，或者声明bean的配置类实例可以自动装配。 后一种方法允许在@Configuration类方法之间进行显式的，IDE友好的导航。
可以在类级别声明或作为元注释声明。
如果需要导入XML或其他非@Configuration bean定义资源，请改用@ImportResource注释。
```
有个很重要的说明是: __允许导入@Configuration类，ImportSelector和ImportBeanDefinitionRegistrar实现,以及常规组件类__

<!--more-->

# <a name="73lrhl"></a>ImportSelector
是一个接口，实现方法用于<span data-type="color" style="color:rgb(61, 70, 77)"><span data-type="background" style="background-color:rgb(255, 255, 255)">返回需要导入的组件的全类名数组，返回结果会被注入容器。spring的事务实现选择`org.springframework.transaction.annotation.TransactionManagementConfigurationSelector`,dubbo的数据绑定选择`org.apache.dubbo.config.spring.context.annotation.DubboConfigConfigurationSelector`</span></span>

# <a name="d3t6kf"></a>ImportBeanDefinitionRegistrar
是一个接口，自定义bean注册到容器

> 一般基于这3个来实现自定义注解的实现，Spring很多功能都是用了这3个，如@EnableXXXX注解。

# <a name="vpcgrz"></a>案例
```java
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EnableClearSelect.class)
public @interface EnableClear {
    boolean custom() default false;
}

```
```java
public class EnableClearSelect implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableClear.class.getName()));
        boolean custom = attributes.getBoolean("custom");
        if(custom){
            return new String[]{Clear1.class.getName()};
        }else {
            return new String[]{Clear2.class.getName()};
        }
    }
}


```
```java
public class Clear1 {
    @PostConstruct
    public void init(){
        System.out.println("Clear1");
    }
}
```
## <a name="kxz6ak"></a>如何使自定义注解生效？
### <a name="1xymle"></a>法1
```java
@Configuration
@EnableClear(custom=true) 
public class MyClearConfig {

}
```
注意点：自定义注解必须被`@Configuration`或`@Component`注解,即MyClearConfig必须是一个bean，自定义注解才会生效
### <a name="bkwbaz"></a>法2
```java
@EnableClear(custom=true) 
public class MyClearConfig {

}
```
```java
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(MyClearConfig .class);
        applicationContext.refresh();
```
法2手动将`MyClearConfig `注入到容器，一般用于写测试用例，正常情况下推荐用法1。

> 注意：如果`Clear1`被其他Spring或者基于Spring自定义注解所注解，那么这些注解也会起生效，只要加入容器，就会起作用，具有连带作用

```java
@EnableScheduling
public class Clear1 {
    @PostConstruct
    public void init(){
        System.out.println("Clear1");
    }

    @Scheduled(fixedRate = 1000)
    public void scheduling(){
        System.out.println("scheduling");
    }

```
会不断的打印`scheduling`


---

学习这个主要是为了学习dubbo数据绑定以及dubbo自定义注解的实现。

