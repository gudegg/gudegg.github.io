---
title: FastJson快速将String转为实体对象
date: 2016-04-17 09:51:04
tags: Json
---

#### 常用fastjson解析API

1. `Person person = JSON.parseObject(json, Person.class);`
1. `List<Person> list = JSON.parseArray(json, Person.class);`
1. `List<Person> list = JSON.parseObject(json, new TypeReference<List<Person>>() {});`
1. `List<Map<String,Object>> list = JSON.parseObject(json, new TypeReference<List<Map<String,Object>>>() {}); `

<!--more-->       

> 当字符串与对象参数不一致时,我们可以使用`new TypeReference()`来进行解析,用`Map<String,Object>`来进行封装,实际上参数不一致使用3也是可以进行解析,只是没有的参数属性会丢失.使用`new TypeReference()`来映射对于返回类型控制会更自由.
详细代码:[Person](https://github.com/gudegg/gudegg.github.io/blob/master/code/Person.java),[JsonTest](https://github.com/gudegg/gudegg.github.io/blob/master/code/JsonTest.java)

![](/images/fastjson.jpg)

