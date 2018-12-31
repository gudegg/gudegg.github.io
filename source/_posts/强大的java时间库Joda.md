---
title: 强大的java时间库Joda
date: 2016-05-25 10:44:54
tags: Joda
---

> [Joda](https://github.com/JodaOrg/joda-time)比JDK自带的Calendar更易用.

<!--more-->

#### 前一天的23:59:59
```java
        DateTime dt=new DateTime().plusDays(-1).withTime(23,59,59,0);
```

#### 今天的0点
```java
        DateTime dt=new DateTime().withTime(0,0,0,0);
```

#### 时间间隔
```java
        //2016-5-21 10:20:20
        DateTime dt1=new DateTime(2016,5,21,10,20,20,0);
        DateTime dt2=new DateTime();
        System.out.println("天数差:"+Days.daysBetween(dt1,dt2).getDays());
        System.out.println("小时差:"+Hours.hoursBetween(dt1,dt2).getHours());
        System.out.println("分钟差:"+Minutes.minutesBetween(dt1,dt2).getMinutes());
```

#### 格式化输出
```java
        DateTime dt4=new DateTime();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println(dt3.toString(fmt));
        System.out.println(dt3.toString("yyyy-MM-dd HH:mm:ss"));
```

#### 增加日期
```java
                DateTime dateTime1 = DateTime.parse("2012-12-03");  
                dateTime1 = dateTime1.plusDays(30);  
                dateTime1 = dateTime1.plusHours(3);  
                dateTime1 = dateTime1.plusMinutes(3);  
                dateTime1 = dateTime1.plusMonths(2);  
                dateTime1 = dateTime1.plusSeconds(4);  
                dateTime1 = dateTime1.plusWeeks(5);  
                dateTime1 = dateTime1.plusYears(3);  
```

#### 是否闰月
```java
                DateTime dt5 = new DateTime();
                DateTime.Property month = dt5.monthOfYear();
                System.out.println("是否闰月:" + month.isLeap());
```
#### 与jdk时间转换
```java
                DateTime dt6 = new DateTime(new Date());    
                Date date = dt6.toDate();  //法1  
                DateTime dt7 = new DateTime(System.currentTimeMillis());    
                Date date2= new Date(dt7.getMillis());//法2
                Calendar calendar = Calendar.getInstance();    
                DateTime dateTime = new DateTime(calendar);
```

还有好多功能 详看官方文档.
参考: [joda-time的使用](http://ylq365.iteye.com/blog/1769680),[官方文档](http://www.joda.org/joda-time/userguide.html)
