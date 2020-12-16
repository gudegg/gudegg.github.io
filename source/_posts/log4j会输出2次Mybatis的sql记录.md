---
title: log4j会输出2次Mybatis的sql记录
date: 2016-04-26 13:09:15
tags: spring
typora-root-url: ..
---

> log4j作为mybatis日志系统时,控制台会输出2次sql记录

#### 解决
![mybatis](https://gitee.com/zhangguodong/image/raw/master/picgo/mybatis.jpg)
1. 法1: 如图所示,没有设置输出到哪里,他会默认继承rootLogger,输出到stdout和file,这样也只会输出一次到控制台的log
2. 法2:
```        
       log4j.additivity.com.mybatis.dao=false #默认为true,打印信息向上级传递,这里即传递给rootLogger,false就不会传递
       log4j.logger.com.mybatis.dao=debug,stdout #我们自己设置输出到stdout,当然后面也可以加file(即也输出到文件日志),
                                                 #因为我们不传递给rootLogger,这样也就只输出到控制台一次,如果additivity
                                                 #不设置就变成默认true就会控制台输出2次日志
```