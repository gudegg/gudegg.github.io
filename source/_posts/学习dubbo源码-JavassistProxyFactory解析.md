---
title: 学习dubbo源码-JavassistProxyFactory解析
date: 2018-10-09 10:01:41
tags: [dubbo]
---

## <a name="r6yslk"></a>JavassistProxyFactory
dubbo是根据javassist动态生成字节码,核心类`com.alibaba.dubbo.common.bytecode.Proxy`和`com.alibaba.dubbo.common.bytecode.Wrapper`

#### <a name="75bsdw"></a>例子:
```java
public interface Gude {
    void setName(String name);
    String getName();
```


<!--more-->

### <a name="9xtgkv"></a>产生代理类

```java
public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
}

```

1. Proxy类的newInstance(InvocationHandler handler)方法是一个抽象方法，所有Proxy.getProxy(interfaces) 要先产生Proxy的代理类，实现newInstance(InvocationHandler handler)方法
2. Proxy会根据interfaces实现抽象方法产生代理类,产生的类名 序号会递增 从0开始 Proxy0 1 2 3… 和proxy0 1 2…对应(包名:com.alibaba.dubbo.common.bytecode)
如下:
1、
```java
public class Proxy0 extends Proxy {

    public Object newInstance(InvocationHandler h) {
        //  return new com.alibaba.dubbo.common.bytecode.proxy0($1); proxy0和接口实现类对应
        return new com.alibaba.dubbo.common.bytecode.proxy0(h);
    }
}
```
2、Gude接口实现类
```java
public class proxy0 implements Gude {

    public static java.lang.reflect.Method[] methods;// 在产生实现类的时候会赋值  clazz.getField("methods").set(null, methods.toArray(new Method[0]));

    private java.lang.reflect.InvocationHandler handler;

    public proxy0() {
    }

    //public <init>(java.lang.reflect.InvocationHandler arg0){handler=$1;}

    public proxy0(InvocationHandler invokerInvocationHandler) {
        this.handler = invokerInvocationHandler;
    }

    public java.lang.String getName() {
        Object[] args = new Object[0];
        Object ret = handler.invoke(this, methods[0], args);
        return (java.lang.String) ret;
    }

    public void setName(java.lang.String arg0) {
        Object[] args = new Object[1];
       // args[0] = ($w) $1; javassist动态编译会替换为如下代码
        args[0] = (Object) arg0;
        Object ret = handler.invoke(this, methods[1], args);
    }
}
```
最终`handler.invoke`会去调用getProxy中的参数`Invoker<T> invoker`

### <a name="20lmqu"></a>产生Invoker

```java
/**
 * @param proxy 具体的实现类
 * @return
 */
 public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }
```

1. 对proxy进行包装并返回包装类，主要核心就是invokeMethod方法，Wrapper的invokeMethod方法是个抽象方法，需要对此方法进行实现，然后创建AbstractProxyInvoker实例返回
2. 动态生成包装类(采用javassist生成，第一次才需要生成，之后直接从缓存取)，主要是对invokeMethod抽象方法进行实现

```java
public class Wrapper{
    public Object invokeMethod(Object instance, String method, Class<?>[] types, Object[] args) throws NoSuchMethodException, InvocationTargetException){
        com.alibaba.dubbo.demo.provider.DemoServiceImpl w;
        try {
            w = ((com.alibaba.dubbo.demo.provider.DemoServiceImpl) $1);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        try {
            /**
            *调用时对接口方法判断 如果有方法重载，还会对参数类型进行判断
            *if( "hello".equals( $2 )  &&  $3.length == 1 &&  $3[0].getName().equals("java.lang.String") ) 
            **/
            if ("gude".equals($2) && $3.length == 1) {
                return ($w) w.gude((java.lang.String) $4[0]);
            }
            if ("sayHello".equals($2) && $3.length == 1) {
                return ($w) w.sayHello((java.lang.String) $4[0]);
            }
        } catch (Throwable e) {
            throw new java.lang.reflect.InvocationTargetException(e);
        }
        throw new com.alibaba.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + $2 + "\" in class com.alibaba.dubbo.demo.provider.DemoServiceImpl.");
    }
    
}
```

Javassist中\$w、\$1含义：


![local5.png | center | 669x445](https://gitee.com/zhangguodong/image/raw/master/null/1539051201848-3c499118-9d11-4a6b-a7eb-92ed73e3ffe9.png "")

[更多详细信息](https://www.jianshu.com/p/b9b3ff0e1bf8)
[javassist官方教程](https://github.com/jboss-javassist/javassist/wiki/Tutorial-2)

* 为什么要包装调用方法?
> 远程到提供者只会传给调用方法名称，如果不动态生成包装类，只能使用反射进行调用，反射相比直接调用方法，性能较低。

