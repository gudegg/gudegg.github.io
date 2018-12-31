---
title: el表达式失效
date: 2016-09-03 17:34:46
tags: jsp
---

今天在用IDEA进行springmvc项目搭建时,jsp中的el表达式死活不解析.查了相关资料终于找到了原因.
idea搭建的maven web项目默认生成的web.xml如下:
```xml
<!DOCTYPE web-app PUBLIC
 "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
 "http://java.sun.com/dtd/web-app_2_3.dtd" >

<web-app>
  <display-name>Archetype Created Web Application</display-name>
</web-app>
```
在servlet2.4之前,isELIgnored默认值为true.(`<%@ page isELIgnored="true"%>` 表示是否禁用EL语言,TRUE表示禁止.FALSE表示不禁,JSP2.0(对应servlet2.4)中默认启用EL语言)

<!--more-->
#### 解决

**法1.** 
> 在jsp页面头加:<%@page isELIgnored="false"%>

**法2. (推荐)** 
> web.xml中的头声明改为2.4或者之后的版本

#### web.xml中各版本声明如下

- 3.1
```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
		 http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
</web-app>
```

- 3.0
```xml
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
	      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	      xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
	      http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
	      version="3.0">
</web-app>
```

- 2.5
```xml
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
	      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	      xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
	      http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
	      version="2.5">
</web-app>
```

- 2.4
```xml
<web-app xmlns="http://java.sun.com/xml/ns/j2ee"
	      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	      xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee
	      http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
	      version="2.4">
	 <display-name>Servlet 2.4 Web Application</display-name>
</web-app>
```

- 2.3(不推荐)
```xml
<!DOCTYPE web-app PUBLIC
 "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
 "http://java.sun.com/dtd/web-app_2_3.dtd" >

<web-app>
  <display-name>Servlet 2.3 Web Application</display-name>
</web-app>
```