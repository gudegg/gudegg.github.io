---
title: springmvc可支持的返回类型
date: 2016-03-03 20:51:20
tags: spring
---

SpringMVC可支持的返回类型:

<!--more-->

>- **Model** is an interface while ModelMap is a class.
>- **ModelAndView** is just a container for both a ModelMap and a view object. It allows a controller to return both as a single value.

Model 是一个接口， 其实现类为ExtendedModelMap，继承了ModelMap类。 

更多可返回类型请查看[中文文档](http://spring.cndocs.tk/mvc.html#mvc-ann-return-types) 、[英文文档](http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#mvc-ann-return-types)