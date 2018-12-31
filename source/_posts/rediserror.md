---
title: could not resolve placeholder ‘redis.host1’ in string value “${redis.host1}”
date: 2016-03-12 10:40:29
tags: spring
---

**原因**:我在spring 的配置文件2处地方导入了配置文件



> Spring容器仅允许**最多定义一个**PropertyPlaceholderConfigurer(或`<context:property-placeholder />`)，其余的会被Spring忽略掉.

<!--more-->

**解决：**  
> 一次性导入多个配置文件
 
```xml
<context:property-placeholder location="classpath:jdbc.properties,classpath:redis.properties" system-properties-mode="NEVER"/> 
```
或者
```xml
<bean class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">  
    <property name="locations">  
       <list>  
          <value>classpath:jdbc.properties</value>  
          <value>classpath:jdbc2.properties</value>  
        </list>  
    </property>  
</bean>
```
