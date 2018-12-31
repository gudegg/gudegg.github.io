---
title: spring用druid做连接池一直出现异常
date: 2016-03-5 10:22:14
tags: spring
---

**[druid](https://github.com/alibaba/druid)**做连接池出现异常Caused by: java.sql.SQLException: Access denied for user ‘Gude’@’localhost’ (using password: YES)

<!--more-->

查了资料([感谢这里的到大神](http://www.oschina.net/question/873438_234580))终于明白



>感觉很奇怪,我明明配置的名称的*root*竟然会变成*Gude*(我的电脑名),原来我在properties的数据库名称用了username，spring引进来就为${username},我数据库配置(db.properties) 用的 `<context:property-placeholder>`而system-properties-mode没有设置用了默认的”**environment**”，改成”**fallback**”即可.system-properties-mode,默认是environment,就是先找系统属性,再找location文件里面的属性.这里他优先找到系统的username(ps:我的电脑名称是Gude)这也就解释了为什么连接数据库变成了电脑名

#### system-properties-mode属性：

> - **ENVIRONMENT** -indicates placeholders should be resolved against the current Environment and against any local properties;(优先系统属性和任何本地属性)  
> - **NEVER** -indicates placeholders should be resolved only against local properties and never against system properties;(只使用本地配置文件)  
> - **FALLBACK** -indicates placeholders should be resolved against any local properties and then against system properties;(先本地配置文件再系统属性)   
> - **OVERRIDE** -indicates placeholders should be resolved first against system properties and then against any local properties;(先系统属性再本地配置文件)  

#### 解决

```xml
<context:property-placeholder location="classpath*:jdbc.properties" system-properties-mode="FALLBACK"/>
```

#### 另外的方法导入配置文件

```xml
  <bean id="propertyConfigurer"
  		class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">
  		<property name="location" value="classpath:jdbc.properties" />
  		<property name="systemPropertiesMode" value="1"/>
  </bean>
```
> 这种方法导入配置文件不会有上面的问题  从官方文档可以看出他只有3种模式 **默认是FALLBACK(即值为1)**

![](/images/Properties.png)


- **3种模式对应的值**

![](/images/Properties_val.png)
 