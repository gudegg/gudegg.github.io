---
title: 学习dubbo源码-服务暴露原理
date: 2018-04-05 14:50:40
tags: dubbo
---


![](/images/provide.png)
[服务提供者暴露一个服务的详细过程](http://dubbo.apache.org/books/dubbo-dev-book/implementation.html)

![](/images/provide2.png)
[暴露服务时序](http://dubbo.apache.org/books/dubbo-dev-book/design.html)

<!--more-->
dubbo有2种方式进行服务暴露：延迟暴露和非延迟暴露
- 延迟暴露：ServiceBean实现InitializingBean，在bean的属性初始化后都会执行该方法。dubbo通过调用afterPropertySet()方法进行服务暴露
```java
	public void afterPropertiesSet() throws Exception {
    ...
    export();
    ...
  }
```
- 非延迟暴露：ServiceBean实现ApplicationListener，在spring实例化bean后会回调onApplicationEvent，dubbo以此调用export()进行发布
```java
  public void onApplicationEvent(ApplicationEvent event) {
        if (ContextRefreshedEvent.class.getName().equals(event.getClass().getName())) {
        	if (isDelay() && ! isExported() && ! isUnexported()) {
                if (logger.isInfoEnabled()) {
                    logger.info("The service ready on spring started. service: " + getInterface());
                }
                export();
            }
        }
    }
```
> dubbo进行服务暴露时，有个参数几乎贯穿调用链，那就是URL属性，所有的bean配置都会转化成URL的参数

#### 核心入口就是ServiceConfig类方法
```java
// 根据不同协议进行遍历暴露
private void doExportUrls() {
        List<URL> registryURLs = loadRegistries(true);
        for (ProtocolConfig protocolConfig : protocols) {
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
 //根据配置做远程暴露和本地暴露   
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName();
        if (name == null || name.length() == 0) {
            name = "dubbo";
        }

        String host = protocolConfig.getHost();
        if (provider != null && (host == null || host.length() == 0)) {
            host = provider.getHost();
        }
       ....//省略代码
        String scope = url.getParameter(Constants.SCOPE_KEY);
        //配置为none不暴露
        if (! Constants.SCOPE_NONE.toString().equalsIgnoreCase(scope)) {

            //配置不是remote的情况下做本地暴露 (配置为remote，则表示只暴露远程服务)
            if (!Constants.SCOPE_REMOTE.toString().equalsIgnoreCase(scope)) {
            //本地暴露
                exportLocal(url);
            }
            //如果配置不是local则暴露为远程服务.(配置为local，则表示只暴露远程服务)
            if (! Constants.SCOPE_LOCAL.toString().equalsIgnoreCase(scope) ){
                if (logger.isInfoEnabled()) {
                    logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                }
                //包含注册中心远程暴露走这个方法
                if (registryURLs != null && registryURLs.size() > 0
                        && url.getParameter("register", true)) {
                    for (URL registryURL : registryURLs) {
                        url = url.addParameterIfAbsent("dynamic", registryURL.getParameter("dynamic"));
                        URL monitorUrl = loadMonitor(registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(Constants.MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                        }
                        //获取AbstractProxyInvoker 实例,默认调用JavassistProxyFactory.getInvoker方法(还有JdkProxyFactory另一个实现)
                        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));

                        Exporter<?> exporter = protocol.export(invoker);
                        exporters.add(exporter);
                    }
                } else {
                    Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);

                    Exporter<?> exporter = protocol.export(invoker);
                    exporters.add(exporter);
                }
            }
        }
        this.urls.add(url);
    }    
```

