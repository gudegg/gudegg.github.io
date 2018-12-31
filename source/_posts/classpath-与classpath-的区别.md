---
title: 'classpath:与classpath*:的区别'
date: 2016-04-26 21:31:59
tags: spring
---

> 在做mybatis多参数的测试时,发现了一个问题`@ContextConfiguration(locations = {"classpath:spring/*.xml"})`导入配置
文件无法成功运行测试,而`@ContextConfiguration(locations = {"classpath*:spring/*.xml"})`却可以

<!--more-->
查了[资料](http://blog.csdn.net/kkdelta/article/details/5507799)终于明白:

1.**classpath:**  只会到指定的classpath路径中查找匹配的资源文件,并且文件如果存在同名,只能加载找到的第一个匹配文件(指定路径本项目存在的话优先从本项目查找)
2.**classpath*:**  从本项目的classpath和所有jar中的classpath加载匹配的资源文件

#### 出错原因

- 我测试类放在test的java/spring下,资源文件没放在test下的resources/spring中,而是放在main的resources/spring中,故`classpath:spring/*.xml`会提示找不到文件(*资源文件应该也要放在test的resources/spring中,因为他会优先从指定路径中查找*),而`classpath*:spring/*.xml`如2中所说的就没有问题,他会查找classpath下所有匹配的(ps:maven项目)
[感谢stackoverflow大佬](http://stackoverflow.com/questions/16985770/runwith-and-contextconfiguration-weird-behaviour),看了这个答案才恍然大悟.

#### `@ContextConfiguration`加载多文件的方式:

1.数组方式
![](/images/spring_success.jpg)
2.正则匹配方式
![](/images/spring_success2.jpg)