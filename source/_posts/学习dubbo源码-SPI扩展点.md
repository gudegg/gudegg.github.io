---
title: 学习dubbo源码-SPI扩展点
date: 2018-02-18 20:54:25
tags: dubbo
---

> 从网上了解到要学习dubbo的源码，必须要从SPI开始，dubbo框架作者从设计之初，就作为一个高可扩的服务框架。

### java SPI
全称(Service Provider Interface),主要作用是提供接口，不同厂商对接口可以有自己不同的实现。我们比较熟悉的就是java.sql.Driver接口。
当厂商提供实现之后，需要在classpath:META-INF/services/下创建服务接口名的文件，并把具体的实现类写进去。
MySql驱动mysql-connector-java中，META-INF/services/文件中就有一个java.sql.Driver文件，内容是:
```java
com.mysql.jdbc.Driver
com.mysql.fabric.jdbc.FabricMySQLDriver
```
我们可以通过如下加载扩展点
```java
ServiceLoader<接口> serviceLoader=ServiceLoader.load(接口.class); 
		
for(接口 cmd:serviceLoader){  
    cmd.method();  
}  
```
[官方文档](https://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html)对ServiceLoader类的功能介绍
<!--more-->
### dubbo不是使用java原生SPI机制

#### 为什么不使用原生的？
1. 原生的一次性加载所有扩展点实现，造成资源的浪费
2. 如果扩展点加载失败，连扩展点的名称都拿不到了。比如：JDK 标准的 ScriptEngine，通过 getName() 获取脚本类型的名称，但如果 RubyScriptEngine 因为所依赖的 jruby.jar 不存在，导致 RubyScriptEngine 类加载失败，这个失败原因被吃掉了，和 ruby 对应不起来，当用户执行 ruby 脚本时，会报不支持 ruby，而不是真正失败的原因。
3. 原生的没有IOC和AOP的功能
4. 原生的不支持缓存和默认值

#### dubbo SPI
> ExtensionLoader类是dubbo实现SPI的核心类，dubbo的扩展都是通过调用ExtensionLoader的getExtension方法加载的,我们主要目的是通过他获取一个实现类。

#### getExtensionLoader 为该接口new一个ExtensionLoader，然后缓存起来。

将断点打在类com.alibaba.dubbo.container.Main的`private static final ExtensionLoader<Container> loader = ExtensionLoader.getExtensionLoader(Container.class);`上
调用过程
```java
ExtensionLoader.getExtensionLoader(Container.class)
EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type)); 
  -->this.type = type;
  -->objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
     -->ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension()
       -->this.type = type;
       -->objectFactory =null;
      
```
执行以上代码完成了2个属性的初始化
1. 每个一个ExtensionLoader都包含了2个值 type 和 objectFactory
  - Class<?> type；//构造器  初始化时要得到的接口名 (标示当前ExtensionLoader所属SPI接口)
  - ExtensionFactory objectFactory//构造器  初始化时 (用于IOC注入) AdaptiveExtensionFactory[SpiExtensionFactory,SpringExtensionFactory]
2. new 一个ExtensionLoader缓存在`ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS`
> 需要特别注意ExtensionFactory也是SPI接口，唯一不同点是他的objectFactory=null

#### getAdaptiveExtension 获取自适应扩展点
Protocol protocol =ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
```java
-->getAdaptiveExtension()//为cachedAdaptiveInstance赋值
  -->createAdaptiveExtension()
    -->getAdaptiveExtensionClass()
      -->getExtensionClasses()//为cachedClasses 赋值
        -->loadExtensionClasses()
          -->loadFile
      -->createAdaptiveExtensionClass()//自动生成和编译一个动态的adpative类，这个类是一个代理类
        -->ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension()
        -->compiler.compile(code, classLoader)
    -->injectExtension()//作用：IOC动态注入  
```
- 主要复杂的过程loadFile：
1. 读取dubbo定义的META-INF/dubbo/internal(dubbo内部默认实现全部放在这个目录，还有2个目录META-INF/dubbo/和META-INF/services/)下定义的当前需要加载的扩展点实现
2. 如果定义的实现类被@Adaptive注解，缓存到cachedAdaptiveClass
3. 只有当该class无adative注解，并且构造函数包含目标接口（type）类型，缓存到cachedWrapperClasses， 例如protocol里面的spi就只有ProtocolFilterWrapper和ProtocolListenerWrapper能命中
4. 如果2和3都不满足，则判断当前实现类是否被@Activate注解，并缓存到cachedActivates中，并将类作为key，实现自定义简称作为val缓存到cachedNames(META-INF/dubbo/internal里面文件的内容是实现自定义简称=具体实现类)
META-INF/dubbo/internal/com.alibaba.dubbo.rpc.Protocol内容
```java
filter=com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper
listener=com.alibaba.dubbo.rpc.protocol.ProtocolListenerWrapper
mock=com.alibaba.dubbo.rpc.support.MockProtocol
```
- createAdaptiveExtensionClass 只有当META-INF/dubbo/internal定义的实现类没有一个被@Adaptive注解才会动态生成代理类
Protocol接口生成的动态类如下：
```java
package com.alibaba.dubbo.rpc;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Protocol$Adaptive implements com.alibaba.dubbo.rpc.Protocol {
    //没有打上@Adaptive的方法如果被调到抛异常
    public void destroy() {
        throw new UnsupportedOperationException("method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
    }

    public int getDefaultPort() {
        throw new UnsupportedOperationException("method public abstract int com.alibaba.dubbo.rpc.Protocol.getDefaultPort() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
    }

    public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg1;
        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.refer(arg0, arg1);
    }

    public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null)
            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
        com.alibaba.dubbo.common.URL url = arg0.getUrl();
        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.export(arg0);
    }
}
```
Transporter接口生成的动态类如下
```java
package com.alibaba.dubbo.remoting;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Transporter$Adaptive implements com.alibaba.dubbo.remoting.Transporter {
    public com.alibaba.dubbo.remoting.Client connect(com.alibaba.dubbo.common.URL arg0, com.alibaba.dubbo.remoting.ChannelHandler arg1) throws com.alibaba.dubbo.remoting.RemotingException {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getParameter("client", url.getParameter("transporter", "netty"));
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.Transporter) name from url(" + url.toString() + ") use keys([client, transporter])");
        com.alibaba.dubbo.remoting.Transporter extension = (com.alibaba.dubbo.remoting.Transporter) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.remoting.Transporter.class).getExtension(extName);
        return extension.connect(arg0, arg1);
    }

    public com.alibaba.dubbo.remoting.Server bind(com.alibaba.dubbo.common.URL arg0, com.alibaba.dubbo.remoting.ChannelHandler arg1) throws com.alibaba.dubbo.remoting.RemotingException {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getParameter("server", url.getParameter("transporter", "netty"));
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.Transporter) name from url(" + url.toString() + ") use keys([server, transporter])");
        com.alibaba.dubbo.remoting.Transporter extension = (com.alibaba.dubbo.remoting.Transporter) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.remoting.Transporter.class).getExtension(extName);
        return extension.bind(arg0, arg1);
    }
}
```
1. 生成动态类名称为**接口名$Adaptive**
1. 生成动态类的前提是SPI接口方法需要被@Adaptive注解，并且方法参数`必须包含URL或者参数有URL成员`(URL为dubbo自定义的,URL几乎贯穿整个SPI接口调用链)，通过URL传入不同的参数动态选择不同的实现
2. 采用哪种方式获取扩展名?`String extName = url.getParameter("server", url.getParameter("transporter", "netty"));`与`String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());`2种方式
    1. 当@Adaptive("xxx")指定为protocol时走`url.getProtocol()`否则走url.getParameter('','默认')，默认值是@SPI('val')中的val
    2.  当默认@Adaptive不指定值时，以扩展点接口名为xxx.
    3.  Protocol接口特殊，它默认方法是不指定值，2中它xxx=protocol，又与1中说的指定为Protocol，所有它走url.getProtocol()
- compiler.compile(code, classLoader)  
1.Compiler也是SPI接口，加载`CompilerClassLoader classLoader = findClassLoader(); com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class)`默认采用javassist实现动态类生成
2.为什么选用javassist? 可以看下作者博客[代理方案性能对比](http://javatar.iteye.com/blog/814426)
- injectExtension
1. 当objectFactory不为空是，实现类有set开头的方法，并且方法参数有SPI接口的方法就能进行注入
2. objectFactory有2个主要实现SpringExtensionFactory和SpiExtensionFactory，SpiExtensionFactory注入的是自适应扩展点(@Adaptive注解的类或者是动态生成的)，SpringExtensionFactory注入的是bean

#### getExtension(String name)通过扩展点名称获取扩展
```java
getExtension(String name) //指定对象缓存在cachedInstances；get出来的对象wrapper对象，例如protocol就是ProtocolFilterWrapper和ProtocolListenerWrapper其中一个。
  -->createExtension(String name)
    -->getExtensionClasses()
    -->injectExtension(T instance)//dubbo的IOC反转控制，就是从spi和spring里面提取对象赋值。
      -->objectFactory.getExtension(pt, property)
        -->SpiExtensionFactory.getExtension(type, name)
          -->ExtensionLoader.getExtensionLoader(type)
          -->loader.getAdaptiveExtension()
        -->SpringExtensionFactory.getExtension(type, name)
          -->context.getBean(name)
    -->injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance))//AOP的简单设计
```
1. 和getAdaptiveExtension很多地方都是一样的，
2. 有个不同就是`injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance))`，当包装类含有当前type的构造函数时，创建此构造函数并进行注入,如果含有包装类，会对包装类进行链式包装(装饰模式),返回最后一个包装类。核心代码如下:
```java
private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, (T) clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            //set方法依赖注入
            injectExtension(instance);
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (wrapperClasses != null && wrapperClasses.size() > 0) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    //包装类注入 注意这里：会进行链式包装
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                }
            }
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance(name: " + name + ", class: " +
                    type + ")  could not be instantiated: " + t.getMessage(), t);
        }
    }
```

### 注解

#### @SPI
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SPI {

    /**
     * 默认扩展点名
     */
    String value() default "";

}
```
1. 只有在接口打了@SPI注解的接口类才会去查找扩展点实现

#### @Adaptive
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {

    /**
     * 从{@link URL}的Key名，对应的Value作为要Adapt成的Extension名。
     * <p>
     * 如果{@link URL}这些Key都没有Value，使用 用 缺省的扩展（在接口的{@link SPI}中设定的值）。<br>
     * 比如，<code>String[] {"key1", "key2"}</code>，表示
     * <ol>
     * <li>先在URL上找key1的Value作为要Adapt成的Extension名；
     * <li>key1没有Value，则使用key2的Value作为要Adapt成的Extension名。
     * <li>key2没有Value，使用缺省的扩展。
     * <li>如果没有设定缺省扩展，则方法调用会抛出{@link IllegalStateException}。
     * </ol>
     * <p>
     * 如果不设置则缺省使用Extension接口类名的点分隔小写字串。<br>
     * 即对于Extension接口{@code com.alibaba.dubbo.xxx.YyyInvokerWrapper}的缺省值为<code>String[] {"yyy.invoker.wrapper"}</code>
     *
     * @see SPI#value()
     */
    String[] value() default {};

}
```
1. 注解在类上作为一个自适应扩展点
2. 注解在方法上，用于动态生成代理类
 
#### @Activate
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Activate {
    /**
     * Group过滤条件。
     * <br />
     * 包含{@link ExtensionLoader#getActivateExtension}的group参数给的值，则返回扩展。
     * <br />
     * 如没有Group设置，则不过滤。
     */
    String[] group() default {};

    /**
     * Key过滤条件。包含{@link ExtensionLoader#getActivateExtension}的URL的参数Key中有，则返回扩展。
     * <p/>
     * 示例：<br/>
     * 注解的值 <code>@Activate("cache,validatioin")</code>，
     * 则{@link ExtensionLoader#getActivateExtension}的URL的参数有<code>cache</code>Key，或是<code>validatioin</code>则返回扩展。
     * <br/>
     * 如没有设置，则不过滤。
     */
    String[] value() default {};

    /**
     * 排序信息，可以不提供。
     */
    String[] before() default {};

    /**
     * 排序信息，可以不提供。
     */
    String[] after() default {};

    /**
     * 排序信息，可以不提供。
     */
    int order() default 0;
}
```
1. 对于集合类扩展点，比如：Filter, InvokerListener, ExportListener, TelnetHandler, StatusChecker 等，可以同时加载多个实现，此时，可以用自动激活来简化配置
2. 可以通过getActivateExtension(URL url, String key)获取激活扩展点集合


[扩展点加载](http://dubbo.io/books/dubbo-dev-book/SPI.html)
  