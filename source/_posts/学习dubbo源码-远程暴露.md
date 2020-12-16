---
title: 学习dubbo源码-远程暴露
date: 2018-04-15 12:12:34
tags: dubbo
---

#### 远程暴露![remote1](https://gitee.com/zhangguodong/image/raw/master/picgo/remote1.png)
1 获取Invoker，本地暴露差不多， 然后通过protocol.export(wrapperInvoker)暴露服务
<!--more-->
![remote2](https://gitee.com/zhangguodong/image/raw/master/picgo/remote2.png)
2 根据URL参数配置获取到RegistryProtocol

![remote3](https://gitee.com/zhangguodong/image/raw/master/picgo/remote3.png)
3 核心发布是RegistryProtocol中的export方法

```java
public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //根据url获取的协议去暴露服务
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);
        //连接到注册中心，并返回注册中心
        final Registry registry = getRegistry(originInvoker);
        //返回注册到注册中心的URL，对URL参数进行一次过滤
        final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);
        //注册数据，比如：提供者地址，消费者地址，路由规则，覆盖规则，等数据。注册中心保存了所有提供者的注册信息供消费者发现
        registry.register(registedProviderUrl);
        // 订阅override数据
        // FIXME 提供者订阅时，会影响同一JVM即暴露服务，又引用同一服务的的场景，因为subscribed以服务名为缓存的key，导致订阅信息覆盖。
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);
        //订阅符合条件的已注册数据，当有注册数据变更时自动推送.比如我现在这里会订阅/dubbo/com.alibaba.dubbo.demo.DemoService/configurators,当有配置发生改变时，会收到通知并进行重新发布
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
        //保证每次export都返回一个新的exporter实例
        return new Exporter<T>() {
            public Invoker<T> getInvoker() {
                return exporter.getInvoker();
            }
            public void unexport() {
            	try {
            		exporter.unexport();
            	} catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	registry.unregister(registedProviderUrl);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	overrideListeners.remove(overrideSubscribeUrl);
                	registry.unsubscribe(overrideSubscribeUrl, overrideSubscribeListener);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
            }
        };
    }
```

![remote4](https://gitee.com/zhangguodong/image/raw/master/picgo/remote4.png)
4 双重检测判断是否已经暴露，并通过invoker获取invokerDelegete ，注意此时的protocol变为了dubbo

![remote5](https://gitee.com/zhangguodong/image/raw/master/picgo/remote5.png)
5 创建DubboInvoker 并放到exportMap中，注意key和本地暴露的区别 本地暴露没有端口号,openServer 开启服务端监听,

![remote6](https://gitee.com/zhangguodong/image/raw/master/picgo/remote6.png)
6 判断是否已经创建服务，未创建的话，创建服务，dubbo为了支持多种服务协议(netty mina等，默认使用netty3)，接下去就是创建服务监听，设置编解码器，不多说了。

![remote7](https://gitee.com/zhangguodong/image/raw/master/picgo/remote7.png)
7 获取注册中心,dubbo注册中心有多种实现 这里使用了最常用zookeeper

![remote8](https://gitee.com/zhangguodong/image/raw/master/picgo/remote8.png)
7 注册到注册中心
可以查看到zookeeper多了providers，如下图
![remote9](https://gitee.com/zhangguodong/image/raw/master/picgo/remote9.png)
`dubbo://192.168.213.1:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&dubbo=2.0.0&generic=false&interface=com.alibaba.dubbo.demo.DemoService&loadbalance=roundrobin&methods=sayHello,gude&owner=william&pid=15140&side=provider&timestamp=1523774349763`

8 订阅注册中心服务 比如我现在这里会订阅`/dubbo/com.alibaba.dubbo.demo.DemoService/configurators`,当有配置发生改变时，会收到通知并进行重新暴露

9 创建并返回Exporter，放到exporters中

#### 总结
> dubbo暴露服务过程很复杂，为了*高可扩展性*，做了很多封装，还有很多细节 主要过程如下：
1. 第一个发布动作：暴露本地服务
1. 第二个发布动作：暴露远程服务
1. 第三个发布动作：启动netty
1. 第四个发布动作：打开连接zk
1. 第五个发布动作：到zk注册
1. 第六个发布动作；监听zk
	


