---
title: 学习dubbo源码-本地暴露
date: 2018-04-07 15:21:11
tags: dubbo
typora-root-url: ..
---

### 本地暴露
![](https://gitee.com/zhangguodong/image/raw/master/picgo/local1.png)
1  主要目的是获取exporter
<!--more-->
![](https://gitee.com/zhangguodong/image/raw/master/picgo/local2.png)
2 获取JavassistProxyFactory来创建invoker
细节：` com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);`获取到的对象是StubProxyFactoryWrapper，在spi扩展点的时候说过，spi实现类含有接口构造会作为一个包装类，如果有多个包装类，会进行链式包装，并返回最后一个类。

![](https://gitee.com/zhangguodong/image/raw/master/picgo/local3.png)
3 对proxy进行包装并返回包装类，主要核心就是invokeMethod方法，Wrapper的invokeMethod方法是个抽象方法，需要对此方法进行实现，然后创建AbstractProxyInvoker实例返回

![](https://gitee.com/zhangguodong/image/raw/master/picgo/local4.png)
4 动态生成包装类(采用javassist生成，第一次才需要生成，之后直接从缓存取)，主要是对invokeMethod抽象方法进行实现

- 看他动态生成的实现方法
```java
    public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws java.lang.reflect.InvocationTargetException {
        com.alibaba.dubbo.demo.provider.DemoServiceImpl w;
        try {
            w = ((com.alibaba.dubbo.demo.provider.DemoServiceImpl) $1);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        try {
            //调用时对接口方法判断
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
```
Javassist中$w、$1含义：
![](https://gitee.com/zhangguodong/image/raw/master/picgo/local5.png)
[详细含义信息请看这里](https://www.jianshu.com/p/b9b3ff0e1bf8)

![](https://gitee.com/zhangguodong/image/raw/master/picgo/local6.png)
![](https://gitee.com/zhangguodong/image/raw/master/picgo/local7.png)

##### 为什么要包装调用方法?

> 远程到提供者只会传给调用方法名称，如果不动态生成包装类，只能使用反射进行调用，反射相比直接调用方法，性能较低。

[oracle官方反射介绍](https://docs.oracle.com/javase/tutorial/reflect/index.html)

5 8个默认过滤器，创建过滤链，对invoker进行相关操作

![](https://gitee.com/zhangguodong/image/raw/master/picgo/local8.png)
6 创建InjvmExporter并放到exporterMap中，不管远程暴露还是本地暴露，都会放到这个Map中。`exporterMap.put(key, this)//本地暴露:key=com.alibaba.dubbo.demo.DemoService, this=InjvmExporter`
`远程暴露: exporterMap.put(key, this)//key=com.alibaba.dubbo.demo.DemoService:20880, this=DubboExporter`

![](https://gitee.com/zhangguodong/image/raw/master/picgo/local9.png)
7 本地暴露完成