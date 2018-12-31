---
title: byteBuddy简单使用
date: 2018-10-03 17:35:13
tags: ByteBuddy
---

背景：项目中遇到model的String类型的属性被赋予空字符串"",希望把它替换为null

### 法1：

直接使用反射将对象的空字符串属性替换

- 缺点：性能差

### 法2：

直接使用ByteBuddy动态生成子类

- 优点：无反射性能好，有必要可以对子类进行缓存

  <!--more-->

Model:

```java
@Data
public class User {
    private String name;
    private Integer age;
}
```

- @Data：为lombok注解 

1、 创建一个拦截器:

```java
public class Intercept {
    @RuntimeType
    public static Object intercept(@This Object proxy, @Argument(0)String arg, @Origin Method method,
                                   @SuperCall Callable<?> callable){
        if(arg!=null&&arg.length()==0){
            return null;
        }
        try {
            //调用父方法
            return callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
```

- @This 调用对象
- @Argument 方法参数(只能选择一个)
- @Arguments 方法所有参数
- @Origin 调用方法
- @SuperCall 回调父类方法

2、 动态生成创建子类

```java
public class Test {
    public static void main(String[] args) throws IllegalAccessException, InstantiationException {
        Class<? extends User> loaded = new ByteBuddy().subclass(User.class).method(ElementMatchers.isSetter().and(ElementMatchers.takesArguments(String.class)))
                .intercept(MethodDelegation.to(Intercept.class)).make().load(Test.class.getClassLoader()).getLoaded();
        User user = loaded.newInstance();
        user.setAge(11);
        user.setName("gude");
        System.out.println(user);

        User user1 = loaded.newInstance();
        user1.setName("");
        user1.setAge(1);
        System.out.println(user1);
    }
}
```

- `ElementMatchers.isSetter().and(ElementMatchers.takesArguments(String.class))`是set方法并且参数是String类型的方法进行拦截



[bytebuddy官方网站](http://bytebuddy.net/)