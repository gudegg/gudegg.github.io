---
title: oracle中null值
date: 2016-03-31 15:31:55
tags: oracle
---

oracle中  a!=null 返回的结果永为`UNKNOWN`
> A condition that evaluates to UNKNOWN acts almost like FALSE(可以把**UNKNOWN**当做false来对待)

<!--more-->


#### 在oracle中where col not in() 中不能使用null值 

```oracle
SELECT * FROM table1 t1 WHERE t1.col1 not in ( 20 , NULL );
```
等价于
```oracle
SELECT * FROM  table1 t1  WHERE t1.col1 != 20 AND t1.col1 != NULL;
```
- t1.col1 != NULL这个结果永远为UNKNOWN 返回的结果集也就是空,但是 in ()可以使用null,没有影响


详情查看[官方文档](https://docs.oracle.com/cd/B19306_01/server.102/b14200/sql_elements005.htm) (ps：在最后面)