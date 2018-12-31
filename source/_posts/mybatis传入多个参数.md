---
title: mybatis传入多个参数
date: 2016-04-21 21:00:33
tags: mybatis
---

#### 多参数

1.普通(按照顺序索引 从0开始)

```java
Map selectManyParam(int uId,String content);

<select id="selectManyParam" resultType="map">
    select * from blog where uId=#{0} and content=#{1}
</select>
```

<!--more-->

2.注解

```java
Map selectManyParam(@Param("id") int uId, @Param("ct") String content);

<select id="selectManyParam" resultType="map">
    select * from blog where uId=#{id} and content=#{ct}
</select>

```

