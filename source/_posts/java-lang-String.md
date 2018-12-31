title: "org.apache.ibatis.reflection.ReflectionException: There is no getter for property named 'name' in 'class java.lang.String'"
date: 2016-06-05 11:18:43
tags: mybatis
---

今天使用mybatis进行如下查询时一直报错:
```xml
select * from people where name like concat('%','${name}','%')
```
<!--more-->
异常:
```
org.apache.ibatis.reflection.ReflectionException: There is no getter for property named 'name' in 'class java.lang.String'
	at org.apache.ibatis.reflection.Reflector.getGetInvoker(Reflector.java:381)
	at org.apache.ibatis.reflection.MetaClass.getGetInvoker(MetaClass.java:164)
	at org.apache.ibatis.reflection.wrapper.BeanWrapper.getBeanProperty(BeanWrapper.java:162)
	at org.apache.ibatis.reflection.wrapper.BeanWrapper.get(BeanWrapper.java:49)
	at org.apache.ibatis.reflection.MetaObject.getValue(MetaObject.java:122)
	at org.apache.ibatis.scripting.xmltags.DynamicContext$ContextMap.get(DynamicContext.java:94)
	at org.apache.ibatis.scripting.xmltags.DynamicContext$ContextAccessor.getProperty(DynamicContext.java:108)
	at org.apache.ibatis.ognl.OgnlRuntime.getProperty(OgnlRuntime.java:2420)
	at org.apache.ibatis.ognl.ASTProperty.getValueBody(ASTProperty.java:114)
	at org.apache.ibatis.ognl.SimpleNode.evaluateGetValueBody(SimpleNode.java:212)
	at org.apache.ibatis.ognl.SimpleNode.getValue(SimpleNode.java:258)
	at org.apache.ibatis.ognl.Ognl.getValue(Ognl.java:494)

```

##### 查了相关博客,终于解决了:
> 对上面的异常，网上说问题原因是Mybatis默认采用OGNL解析参数(ps:还不懂,得恶补)，所以会自动采用对象树的形式获取传入的变量值，解决方法有两个：

1. 将参数名（上面的例子为'name'）替换为"_parameter" ，即：
```xml
select * from people where name like concat('%','${_parameter}','%')
```

2. 在接口中定义方法时 增加“@Param("参数名")” 标记 如:
```java
public List<People> findSomePeople(@Param("name") String name);
```

##### github大神相关问题的回答:

- As documented, `parameterType` is optional and it is usually better to let MyBatis detect it.
- As your statement has one simple type parameter without `@Param` annotation, you need to use the implicit name `_parameter` to reference it in the OGNL expression or ${}.
- Java does not allow us to obtain the parameter name at runtime, unfortunately.Please search 'java parameter name' for more detailed explanation.

So, as I explained, you may have to 
1) modify the xml mapper as follows:
```xml
    <when test="_parameter &lt; 0">
      id = 0-#{id}
    </when>
```
Or 2) add @Param("id") to the method parameter.
```java
    User findById(@Param("id")int id);
```