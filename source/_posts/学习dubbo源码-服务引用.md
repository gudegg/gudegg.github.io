---
title: 学习dubbo源码-服务引用
date: 2018-05-26 15:56:01
tags: dubbo
typora-root-url: ..
---

```xml
<dubbo:reference id="demoService" interface="com.alibaba.dubbo.demo.DemoService" timeout="60000" version="10.11.11" group="guodong"/>
```

## 入口
DubboNamespaceHandler配置了自定义dubbo标签的解析。
```java
public class DubboNamespaceHandler extends NamespaceHandlerSupport {

	static {
		Version.checkDuplicate(DubboNamespaceHandler.class);
	}

	public void init() {
	      registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new DubboBeanDefinitionParser(AnnotationBean.class, true));
    }

}
```
DubboBeanDefinitionParser会对标签进行解析，并把相关参数设置到RootBeanDefinition，并返回它。RootBeanDefinition有个很关键的方法setBeanClass，这里设置的beanClass为ReferenceBean。
![](https://gitee.com/zhangguodong/image/raw/master/picgo/referenBean%E5%85%B3%E7%B3%BB%E5%9B%BE.png)

ReferenceBean实现了FactoryBean,其返回的Bean对象不是指定类的一个实例，而是该FactoryBean的getObject方法所返回的对象。
ReferenceBean的getObject方法会调用ReferenceConfig的get()方法。

```java
public class ReferenceConfig<T> extends AbstractReferenceConfig{
    public synchronized T get() {
            if (destroyed){
                throw new IllegalStateException("Already destroyed!");
            }
            if (ref == null) {
                init();
            }
            return ref;
        }
}
```
核心就是init(),它会对配置进行检测并创建代理 createProxy(map);

<!--more-->

## 创建代理
```java
public class ReferenceConfig<T> extends AbstractReferenceConfig{
	private T createProxy(Map<String, String> map) {
        	//引用服务
	        //1 判断是不是本地引用
            //2 根据指定url判断对点对直连地址还是注册中心地址
            //3 单注册中心或者多注册中心服务引用 
          //registry://54.249.216.148:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&dubbo=2.0.0&pid=16620&refer=application=demo-consumer&dubbo=2.0.0&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello,gude&pid=16620&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527326701953&version=10.11.11&registry=zookeeper&timestamp=1527326703926
        //默认 最终返回FailoverClusterInvoker
            invoker=refprotocol.refer(interfaceClass, urls.get(0));//Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension() 动态产生自适应扩展点Protocol$Adpative
            // 创建invoker代理
            return (T) proxyFactory.getProxy(invoker);
    }
}   
```
### 引用服务
使用zk作为注册中心
```java
public class Protocol$Adpative implements com.alibaba.dubbo.rpc.Protocol {
     ...
    public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg1;
        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());//extName=registry
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);//返回RegistryProtocol包装后的对象ProtocolListenerWrapper
        return extension.refer(arg0, arg1);
    }
}
```
先ProtocolListenerWrapper，再进入ProtocolFilterWrapper，由于使用registry协议，这2个Wrapper类不做处理。最后到达RegistryProtocol

```java
	public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        //协议由registry变为zookeeper
        //zookeeper://54.249.216.148:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&dubbo=2.0.0&pid=16620&refer=application=demo-consumer&dubbo=2.0.0&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello,gude&pid=16620&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527326701953&version=10.11.11&timestamp=1527326703926
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        //获取注册中心 zk返回的是ZookeeperRegistry
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
        	return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0 ) {
            if ( ( Constants.COMMA_SPLIT_PATTERN.split( group ) ).length > 1
                    || "*".equals( group ) ) {
                return doRefer( getMergeableCluster(), registry, type, url );
            }
        }
        //根据集群策略返回Invoker，默认为failover 即失败转移
        return doRefer(cluster, registry, type, url);
    }
```
#### 获取注册中心

```java
public class RegistryFactory$Adpative implements com.alibaba.dubbo.registry.RegistryFactory {
    public com.alibaba.dubbo.registry.Registry getRegistry(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());//extName=zookeeper
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.registry.RegistryFactory) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.registry.RegistryFactory extension = (com.alibaba.dubbo.registry.RegistryFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.registry.RegistryFactory.class).getExtension(extName);
        //extension=ZookeeperRegistryFactory
        return extension.getRegistry(arg0);
    }
}
```
ZookeeperRegistryFactory继承AbstractRegistryFactory，会调用AbstractRegistryFactory的getRegistry
```java
 public Registry getRegistry(URL url) {
 //zookeeper://54.249.216.148:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=16620&timestamp=1527326703926
    	url = url.setPath(RegistryService.class.getName())
    			.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
    			.removeParameters(Constants.EXPORT_KEY, Constants.REFER_KEY);
    	//zookeeper://54.249.216.148:2181/com.alibaba.dubbo.registry.RegistryService
      String key = url.toServiceString();
        // 锁定注册中心获取过程，保证注册中心单一实例
        LOCK.lock();
        try {
            //先从缓存获取
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            //ZookeeperRegistryFactory的createRegistry
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            //加到缓存
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // 释放锁
            LOCK.unlock();
        }
    }
```
ZookeeperRegistryFactory的createRegistry方法：
```java
public class ZookeeperRegistryFactory extends AbstractRegistryFactory {
	
	private ZookeeperTransporter zookeeperTransporter;
    //ExtensionLoader获取扩展时会对set方法进行自动注入，这里会注入ZookeeperTransporter$Adaptive
    public void setZookeeperTransporter(ZookeeperTransporter zookeeperTransporter) {
		this.zookeeperTransporter = zookeeperTransporter;
	}

	public Registry createRegistry(URL url) {
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }

}
```
ExtensionLoader自动注入如下：
```java
private T injectExtension(T instance) {
        try {
            if (objectFactory != null) {
                for (Method method : instance.getClass().getMethods()) {
                    if (method.getName().startsWith("set")
                            && method.getParameterTypes().length == 1
                            && Modifier.isPublic(method.getModifiers())) {
                        Class<?> pt = method.getParameterTypes()[0];
                        try {
                            String property = method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
                            //objectFactory=SpringExtensionFactory或者SpiExtensionFactory，objectFactory.getExtension最终调用：SpiExtensionFactory调用的是getAdaptiveExtension，SpringExtensionFactory调用getExtension(Class<T> type, String name)，SPI扩展点objectFactory为SpiExtensionFactory，property在SpringExtensionFactory时才起作用
                            Object object = objectFactory.getExtension(pt, property);
                            if (object != null) {
                                method.invoke(instance, object);
                            }
                        } catch (Exception e) {
                            logger.error("fail to inject via method " + method.getName()
                                    + " of interface " + type.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }
```
```java
public class ZookeeperTransporter$Adaptive implements com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter {
    public com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient connect(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getParameter("client", url.getParameter("transporter", "zkclient"));//默认使用zkclient,新版已经改为curator
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter) name from url(" + url.toString() + ") use keys([client, transporter])");
        com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter extension = (com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter.class).getExtension(extName);
        //extension=ZkclientZookeeperTransporter
        return extension.connect(arg0);
    }
}
```

ZookeeperRegistryFactory的new ZookeeperRegistry(url, zookeeperTransporter):
```java
    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        //会调用AbstractRegistry和FailbackRegistry
        super(url);
        if (url.isAnyHost()) {
    		throw new IllegalStateException("registry address == null");
    	}
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
        if (! group.startsWith(Constants.PATH_SEPARATOR)) {
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group;
        //连接到zk
        zkClient = zookeeperTransporter.connect(url);
        //添加重连监听
        zkClient.addStateListener(new StateListener() {
            public void stateChanged(int state) {
            	if (state == RECONNECTED) {
	            	try {
						recover();
					} catch (Exception e) {
						logger.error(e.getMessage(), e);
					}
            	}
            }
        });
    }
```
zookeeperTransporter.connect(url)调用过程如下:
ZookeeperTransporter$Adaptive(自适应扩展点,代码在上面已经说明)->ZkclientZookeeperTransporter(new ZkclientZookeeperClient(url))->ZkclientZookeeperClient

```java
public class ZkclientZookeeperClient extends AbstractZookeeperClient<IZkChildListener> {

	private final ZkClient client;

	private volatile KeeperState state = KeeperState.SyncConnected;

	public ZkclientZookeeperClient(URL url) {
		super(url);
    //连接到zk
		client = new ZkClient(url.getBackupAddress());
    //订阅状态改变
		client.subscribeStateChanges(new IZkStateListener() {
			public void handleStateChanged(KeeperState state) throws Exception {
				ZkclientZookeeperClient.this.state = state;
				//将zkclient的状态转化为Dubbo的自定义状态，达到zk不同客户端实现，上层只需执行相同的操作
        if (state == KeeperState.Disconnected) {
					stateChanged(StateListener.DISCONNECTED);
				} else if (state == KeeperState.SyncConnected) {
					stateChanged(StateListener.CONNECTED);
				}
			}
			public void handleNewSession() throws Exception {
				stateChanged(StateListener.RECONNECTED);
			}
		});
	}
 }  
```

```java
public AbstractRegistry(URL url) {
        // registryUrl = url;
        setUrl(url);
        // 启动文件保存定时器
        syncSaveFile = url.getParameter(Constants.REGISTRY_FILESAVE_SYNC_KEY, false);
        //dubbo会在用户的.dubbo下生成每个注册中心对应缓存 如本机：C:\Users\Gude/.dubbo/dubbo-registry-54.249.216.148.cache，其中特殊的key值.registies记录注册中心列表，其它均为notified服务提供者列表
        String filename = url.getParameter(Constants.FILE_KEY, System.getProperty("user.home") + "/.dubbo/dubbo-registry-" + url.getHost() + ".cache");
        File file = null;
        if (ConfigUtils.isNotEmpty(filename)) {
            file = new File(filename);
            if(! file.exists() && file.getParentFile() != null && ! file.getParentFile().exists()){
                if(! file.getParentFile().mkdirs()){
                    throw new IllegalArgumentException("Invalid registry store file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                }
            }
        }
        this.file = file;
        //将.cache内容读到properties
        loadProperties();
        //订阅通知， BackupUrls 注册中心如果单节点则订阅当前节点，如果多节点(集群)会订阅每个节点
        notify(url.getBackupUrls());
    }
```
##### 获取注册中心时订阅通知

由于还没订阅,getSubscribed() size为0 什么都不做

```java
public abstract class AbstractRegistry implements Registry {
    protected void notify(List<URL> urls) {
            if(urls == null || urls.isEmpty()) return;

            for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) {
                URL url = entry.getKey();

                if(! UrlUtils.isMatch(url, urls.get(0))) {
                    continue;
                }

                Set<NotifyListener> listeners = entry.getValue();
                if (listeners != null) {
                    for (NotifyListener listener : listeners) {
                        try {
                            notify(url, listener, filterEmpty(url, urls));
                        } catch (Throwable t) {
                            logger.error("Failed to notify registry event, urls: " +  urls + ", cause: " + t.getMessage(), t);
                        }
                    }
                }
            }
        }
}    
```

AbstractRegistry构造执行完成接下去执行是FailbackRegistry
```java
 public FailbackRegistry(URL url) {
        super(url);
        //默认5s 重试一次
        int retryPeriod = url.getParameter(Constants.REGISTRY_RETRY_PERIOD_KEY, Constants.DEFAULT_REGISTRY_RETRY_PERIOD);
        this.retryFuture = retryExecutor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                // 检测并连接注册中心
                try {
                    retry();
                } catch (Throwable t) { // 防御性容错
                    logger.error("Unexpected error occur at failed retry, cause: " + t.getMessage(), t);
                }
            }
        }, retryPeriod, retryPeriod, TimeUnit.MILLISECONDS);
    }
```
注册失败的url会放到Set<URL> failedRegistered = new ConcurrentHashSet<URL>(),retry()会每5s进行重试。retry会进行如下动作：doRegister(注册)  doUnregister(url) doSubscribe(订阅)  doUnsubscribe(url, listener); listener.notify(urls)(通知);

#### 引用远程服务

```java
public class RegistryProtocol implements Protocol {

  private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        //registry为ZookeeperRegistry
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        //consumer://192.168.213.1/com.alibaba.dubbo.demo.DemoService?application=demo-consumer&dubbo=2.0.0&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello,gude&pid=5772&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527330877579&version=10.11.11
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (! Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
                //向注册中心注册服务
            registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY,
                    Constants.CHECK_KEY, String.valueOf(false)));
        }
        //订阅服务提供者
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY, 
                Constants.PROVIDERS_CATEGORY 
                + "," + Constants.CONFIGURATORS_CATEGORY 
                + "," + Constants.ROUTERS_CATEGORY));
        	//directory=RegistryDirectory， cluster为Cluster$Adpative
      		//多个 Invoker 伪装成一个 Invoker
      		return cluster.join(directory);
    }
}
```
官方集群容错图：

![](https://gitee.com/zhangguodong/image/raw/master/picgo/cluster.jpg)

[官方文档](http://dubbo.apache.org/books/dubbo-user-book/demos/fault-tolerent-strategy.html)

各节点关系：

- 这里的 `Invoker` 是 `Provider` 的一个可调用 `Service` 的抽象，`Invoker` 封装了 `Provider` 地址及 `Service` 接口信息
- `Directory` 代表多个 `Invoker`，可以把它看成 `List<Invoker>` ，但与 `List` 不同的是，它的值可能是动态变化的，比如注册中心推送变更
- `Cluster` 将 `Directory` 中的多个 `Invoker` 伪装成一个 `Invoker`，对上层透明，伪装过程包含了容错逻辑，调用失败后，重试另一个
- `Router` 负责从多个 `Invoker` 中按路由规则选出子集，比如读写分离，应用隔离等
- `LoadBalance` 负责从多个 `Invoker` 中选出具体的一个用于本次调用，选的过程包含了负载均衡算法，调用失败后，需要重选

##### 注册服务到注册中心

FailbackRegistry(register())->AbstractRegistry (register())->FailbackRegistry->ZookeeperRegistry (doRegister())
```java
public abstract class AbstractRegistry implements Registry {
 public void register(URL url) {
 //consumer://192.168.213.1/com.alibaba.dubbo.demo.DemoService?application=demo-consumer&category=consumers&check=false&dubbo=2.0.0&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello,gude&pid=5772&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527330877579&version=10.11.11
        if (url == null) {
            throw new IllegalArgumentException("register url == null");
        }
        if (logger.isInfoEnabled()){
            logger.info("Register: " + url);
        }
        //加到set
        registered.add(url);
    }
}    
```

```java
@Override
public abstract class FailbackRegistry extends AbstractRegistry{
    public void register(URL url) {
        super.register(url);
        failedRegistered.remove(url);
        failedUnregistered.remove(url);
        try {
            // 向服务器端发送注册请求 
            doRegister(url);
        } catch (Exception e) {
            Throwable t = e;

            // 如果开启了启动时检测，则直接抛出异常
            boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                    && url.getParameter(Constants.CHECK_KEY, true)
                    && ! Constants.CONSUMER_PROTOCOL.equals(url.getProtocol());
            boolean skipFailback = t instanceof SkipFailbackWrapperException;
            if (check || skipFailback) {
                if(skipFailback) {
                    t = t.getCause();
                }
                throw new IllegalStateException("Failed to register " + url + " to registry " + getUrl().getAddress() + ", cause: " + t.getMessage(), t);
            } else {
                logger.error("Failed to register " + url + ", waiting for retry, cause: " + t.getMessage(), t);
            }

            // 将失败的注册请求记录到失败列表，定时重试
            failedRegistered.add(url);
        }
    }
}
```
```java
public class ZookeeperRegistry extends FailbackRegistry
   protected void doRegister(URL url) {
        try {
          //zkClient=ZkclientZookeeperClient,create在ZkclientZookeeperClient的继承抽象类AbstractZookeeperClient
          //默认为临时节点url.getParameter(Constants.DYNAMIC_KEY, true)
        	zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
}    
```
最终创建节点
```java
public void create(String path, boolean ephemeral) {  //path=/dubbo/com.alibaba.dubbo.demo.DemoService/consumers/consumer%3A%2F%2F192.168.213.1%2Fcom.alibaba.dubbo.demo.DemoService%3Fapplication%3Ddemo-consumer%26category%3Dconsumers%26check%3Dfalse%26dubbo%3D2.0.0%26group%3Dguodong%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%2Cgude%26pid%3D5772%26revision%3D10.11.11%26side%3Dconsumer%26timeout%3D60000%26timestamp%3D1527330877579%26version%3D10.11.11
		int i = path.lastIndexOf('/');
		if (i > 0) {
      //递归创建父节点为持久节点
			create(path.substring(0, i), false);
		}
    //最后创建临时节点 /dubbo/com.alibaba.dubbo.demo.DemoService/consumers/consumer%3A%2F%2F192.168.213.1%2Fcom.alibaba.dubbo.demo.DemoService%3Fapplication%3Ddemo-consumer%26category%3Dconsumers%26check%3Dfalse%26dubbo%3D2.0.0%26group%3Dguodong%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%2Cgude%26pid%3D5772%26revision%3D10.11.11%26side%3Dconsumer%26timeout%3D60000%26timestamp%3D1527330877579%26version%3D10.11.11
		if (ephemeral) {
			createEphemeral(path);
		} else {
			createPersistent(path);
		}
	}
```
zk创建的节点如下:
![](https://gitee.com/zhangguodong/image/raw/master/picgo/zk.png)

##### 订阅服务提供者
RegistryDirectory( subscribe())->FailbackRegistry(subscribe())->AbstractRegistry (subscribe())->FailbackRegistry->ZookeeperRegistry (doRegister())

```java
public class RegistryDirectory<T> extends AbstractDirectory<T> implements NotifyListener{ 
  public void subscribe(URL url) {
  //url=consumer://192.168.213.1/com.alibaba.dubbo.demo.DemoService?application=demo-consumer&category=providers,configurators,routers&dubbo=2.0.0&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello,gude&pid=12724&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527341060244&version=10.11.11
        setConsumerUrl(url);
        //ZookeeperRegistry
        registry.subscribe(url, this);
    }
 }    
```
AbstractRegistry:
```java
  public void subscribe(URL url, NotifyListener listener) {
         //listener为RegistryDirectory ，RegistryDirectory实现了NotifyListener
        if (url == null) {
            throw new IllegalArgumentException("subscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("subscribe listener == null");
        }
        if (logger.isInfoEnabled()){
            logger.info("Subscribe: " + url);
        }
        //获取当前url是否已经存在监听集合，不存在则创建，并放到缓存
        Set<NotifyListener> listeners = subscribed.get(url);
        if (listeners == null) {
            subscribed.putIfAbsent(url, new ConcurrentHashSet<NotifyListener>());
            listeners = subscribed.get(url);
        }
        //监听器放到当前url为key的缓存集合中
        listeners.add(listener);
    }
```
FailbackRegistry:
```java
 @Override
    public void subscribe(URL url, NotifyListener listener) {
        //调用AbstractRegistry.subscribe
        super.subscribe(url, listener);
        //failedUnsubscribed和failedNotified中移除当前监听器
        removeFailedSubscribed(url, listener);
        try {
            // 向服务器端发送订阅请求
            doSubscribe(url, listener);
        } catch (Exception e) {
            Throwable t = e;

            List<URL> urls = getCacheUrls(url);
            if (urls != null && urls.size() > 0) {
                notify(url, listener, urls);
                logger.error("Failed to subscribe " + url + ", Using cached list: " + urls + " from cache file: " + getUrl().getParameter(Constants.FILE_KEY, System.getProperty("user.home") + "/dubbo-registry-" + url.getHost() + ".cache") + ", cause: " + t.getMessage(), t);
            } else {
                // 如果开启了启动时检测，则直接抛出异常
                boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                        && url.getParameter(Constants.CHECK_KEY, true);
                boolean skipFailback = t instanceof SkipFailbackWrapperException;
                if (check || skipFailback) {
                    if(skipFailback) {
                        t = t.getCause();
                    }
                    throw new IllegalStateException("Failed to subscribe " + url + ", cause: " + t.getMessage(), t);
                } else {
                    logger.error("Failed to subscribe " + url + ", waiting for retry, cause: " + t.getMessage(), t);
                }
            }

            // 将失败的订阅请求记录到失败列表，定时重试
            addFailedSubscribed(url, listener);
        }
    }
```
ZookeeperRegistry:
```java
 protected void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            //Constants.ANY_VALUE=* 这里走else逻辑
            if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, new ChildListener() {
                        public void childChanged(String parentPath, List<String> currentChilds) {
                            for (String child : currentChilds) {
								child = URL.decode(child);
                                if (! anyServices.contains(child)) {
                                    anyServices.add(child);
                                    subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child, 
                                            Constants.CHECK_KEY, String.valueOf(false)), listener);
                                }
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (services != null && services.size() > 0) {
                    for (String service : services) {
						service = URL.decode(service);
						anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service, 
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<URL>();
                //toCategoriesPath(url)->/dubbo/com.alibaba.dubbo.demo.DemoService/providers、/dubbo/com.alibaba.dubbo.demo.DemoService/configurators、/dubbo/com.alibaba.dubbo.demo.DemoService/routers
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                    if (listeners == null) {
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                        listeners = zkListeners.get(url);
                    }
                    
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                    //主要目的就是将zkClient的事件IZkChildListener转换到ZookeeperRegistry事件NotifyListener，当zkClient子目录发生改变时，会调用下面的ZookeeperRegistry.this.notify
                        listeners.putIfAbsent(listener, new ChildListener() {
                            public void childChanged(String parentPath, List<String> currentChilds) {
                            	ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds));
                            }
                        });
                        zkListener = listeners.get(listener);
                    }
                    //创建3个节点/dubbo/com.alibaba.dubbo.demo.DemoService/providers、/dubbo/com.alibaba.dubbo.demo.DemoService/configurators、/dubbo/com.alibaba.dubbo.demo.DemoService/routers
                    zkClient.create(path, false);
                    //zkclient将会监听路径下的变化 最终会调用ZkclientZookeeperClient下的client.subscribeChildChanges(path, listener)，这个会回调上面的ZookeeperRegistry.this.notify();
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                    	urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                //第一次订阅通知 ZookeeperRegistry.this.notify()
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
```
>  List<String> children = zkClient.addChildListener(path, zkListener)非常重要，/dubbo/com.alibaba.dubbo.demo.DemoService/providers、/dubbo/com.alibaba.dubbo.demo.DemoService/configurators、/dubbo/com.alibaba.dubbo.demo.DemoService/routers配置改变时，都会收到通知，ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds));会对通知进行处理，比如重新刷新Invoker

`ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap<URL, ConcurrentMap<NotifyListener, ChildListener>>();`NotifyListener作为key,key就是RegistryDirectory

###### 服务订阅完成后通知 

**核心：providers、routers、configurators目录下发生改变会调用这个通知**

```java
public abstract class FailbackRegistry extends AbstractRegistry{
@Override
    protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        try {
        	doNotify(url, listener, urls);
        } catch (Exception t) {
            // 将失败的通知请求记录到失败列表，定时重试
            Map<NotifyListener, List<URL>> listeners = failedNotified.get(url);
            if (listeners == null) {
                failedNotified.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, List<URL>>());
                listeners = failedNotified.get(url);
            }
            listeners.put(listener, urls);
            logger.error("Failed to notify for subscribe " + url + ", waiting for retry, cause: " + t.getMessage(), t);
        }
    }
    
    protected void doNotify(URL url, NotifyListener listener, List<URL> urls) {
        //还是调用AbstractRegistry的notify,注意重载方法
    	super.notify(url, listener, urls);
    }
}    
```

```java
public abstract class AbstractRegistry implements Registry {
   protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        if ((urls == null || urls.size() == 0) 
                && ! Constants.ANY_VALUE.equals(url.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + url);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + url + ", urls: " + urls);
        }
        Map<String, List<URL>> result = new HashMap<String, List<URL>>();
        for (URL u : urls) {
            if (UrlUtils.isMatch(url, u)) {
            	String category = u.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
            	List<URL> categoryList = result.get(category);
            	if (categoryList == null) {
            		categoryList = new ArrayList<URL>();
            		result.put(category, categoryList);
            	}
            	categoryList.add(u);
            }
        }
        if (result.size() == 0) {
            return;
        }
        Map<String, List<URL>> categoryNotified = notified.get(url);
        if (categoryNotified == null) {
            notified.putIfAbsent(url, new ConcurrentHashMap<String, List<URL>>());
            categoryNotified = notified.get(url);
        }
        //providers，configurators，routers三个类别分别进行通知
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            //异步保存配置到用户目录下的.dubbo/xxx.cache中
            saveProperties(url);
            //这里的listener是RegistryDirectory
            listener.notify(categoryList);
        }
    }
}    
```

**最终目的：刷新urlInvokerMap 和methodInvokerMap对象 **

```java
public class RegistryDirectory<T> extends AbstractDirectory<T> implements NotifyListener {
     //重要  Map<url, Invoker> cache service url to invoker mapping. 
    private volatile Map<String, Invoker<T>> urlInvokerMap; // 初始为null以及中途可能被赋为null，请使用局部变量引用
    //重要  Map<methodName, Invoker> cache service method to invokers mapping.
    private volatile Map<String, List<Invoker<T>>> methodInvokerMap; // 初始为null以及中途可能被赋为null，请使用局部变量引用
    
    public synchronized void notify(List<URL> urls) {
            List<URL> invokerUrls = new ArrayList<URL>();
            List<URL> routerUrls = new ArrayList<URL>();
            List<URL> configuratorUrls = new ArrayList<URL>();
            for (URL url : urls) {
                String protocol = url.getProtocol();
                String category = url.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
                if (Constants.ROUTERS_CATEGORY.equals(category) 
                        || Constants.ROUTE_PROTOCOL.equals(protocol)) {
                    routerUrls.add(url);
                } else if (Constants.CONFIGURATORS_CATEGORY.equals(category) 
                        || Constants.OVERRIDE_PROTOCOL.equals(protocol)) {
                    configuratorUrls.add(url);
                } else if (Constants.PROVIDERS_CATEGORY.equals(category)) {
                    invokerUrls.add(url);
                } else {
                    logger.warn("Unsupported category " + category + " in notified url: " + url + " from registry " + getUrl().getAddress() + " to consumer " + NetUtils.getLocalHost());
                }
            }
            // 更新当前接口的configurators 
            if (configuratorUrls != null && configuratorUrls.size() >0 ){
                this.configurators = toConfigurators(configuratorUrls);
            }
            //  更新当前接口的routers
            if (routerUrls != null && routerUrls.size() >0 ){
                List<Router> routers = toRouters(routerUrls);
                if(routers != null){ // null - do nothing
                    setRouters(routers);
                }
            }
            List<Configurator> localConfigurators = this.configurators; // local reference
            // 合并override参数
            this.overrideDirectoryUrl = directoryUrl;
            if (localConfigurators != null && localConfigurators.size() > 0) {
                for (Configurator configurator : localConfigurators) {
                    this.overrideDirectoryUrl = configurator.configure(overrideDirectoryUrl);
                }
            }
            // category为providers才会刷新invoker实例
            refreshInvoker(invokerUrls);
        }
     /**
     * 根据invokerURL列表转换为invoker列表。转换规则如下：
     * 1.如果url已经被转换为invoker，则不在重新引用，直接从缓存中获取，注意如果url中任何一个参数变更也会重新引用
     * 2.如果传入的invoker列表不为空，则表示最新的invoker列表
     * 3.如果传入的invokerUrl列表是空，则表示只是下发的override规则或route规则，需要重新交叉对比，决定是否需要重新引用。
     * @param invokerUrls 传入的参数不能为null
     */
    private void refreshInvoker(List<URL> invokerUrls){
        if (invokerUrls != null && invokerUrls.size() == 1 && invokerUrls.get(0) != null
                && Constants.EMPTY_PROTOCOL.equals(invokerUrls.get(0).getProtocol())) {
            this.forbidden = true; // 禁止访问
            this.methodInvokerMap = null; // 置空列表
            destroyAllInvokers(); // 关闭所有Invoker
        } else {
            this.forbidden = false; // 允许访问
            Map<String, Invoker<T>> oldUrlInvokerMap = this.urlInvokerMap; // local reference
            if (invokerUrls.size() == 0 && this.cachedInvokerUrls != null){
                invokerUrls.addAll(this.cachedInvokerUrls);
            } else {
                this.cachedInvokerUrls = new HashSet<URL>();
                this.cachedInvokerUrls.addAll(invokerUrls);//缓存invokerUrls列表，便于交叉对比
            }
            if (invokerUrls.size() ==0 ){
            	return;
            }
            //url为key
            Map<String, Invoker<T>> newUrlInvokerMap = toInvokers(invokerUrls) ;// 将URL列表转成Invoker列表
            Map<String, List<Invoker<T>>> newMethodInvokerMap = toMethodInvokers(newUrlInvokerMap); // 换方法名映射Invoker列表 
            // state change
            //如果计算错误，则不进行处理.
            if (newUrlInvokerMap == null || newUrlInvokerMap.size() == 0 ){
                logger.error(new IllegalStateException("urls to invokers error .invokerUrls.size :"+invokerUrls.size() + ", invoker.size :0. urls :"+invokerUrls.toString()));
                return ;
            }
            //方法名为key,一个接口有多个提供者，一个方法也就有多个Invoker
            this.methodInvokerMap = multiGroup ? toMergeMethodInvokerMap(newMethodInvokerMap) : newMethodInvokerMap;
            this.urlInvokerMap = newUrlInvokerMap;
            try{
                destroyUnusedInvokers(oldUrlInvokerMap,newUrlInvokerMap); // 关闭未使用的Invoker
            }catch (Exception e) {
                logger.warn("destroyUnusedInvokers error. ", e);
            }
        }
    }
      /**
     * 将urls转成invokers,如果url已经被refer过，不再重新引用。
     * 
     * @param urls 提供者列表
     * @return invokers
     */
    private Map<String, Invoker<T>> toInvokers(List<URL> urls) {
        Map<String, Invoker<T>> newUrlInvokerMap = new HashMap<String, Invoker<T>>();
        if(urls == null || urls.size() == 0){
            return newUrlInvokerMap;
        }
        Set<String> keys = new HashSet<String>();
        String queryProtocols = this.queryMap.get(Constants.PROTOCOL_KEY);
        for (URL providerUrl : urls) {
        	//如果reference端配置了protocol，则只选择匹配的protocol
        	if (queryProtocols != null && queryProtocols.length() >0) {
        		boolean accept = false;
        		String[] acceptProtocols = queryProtocols.split(",");
        		for (String acceptProtocol : acceptProtocols) {
        			if (providerUrl.getProtocol().equals(acceptProtocol)) {
        				accept = true;
        				break;
        			}
        		}
        		if (!accept) {
        			continue;
        		}
        	}
            if (Constants.EMPTY_PROTOCOL.equals(providerUrl.getProtocol())) {
                continue;
            }
            if (! ExtensionLoader.getExtensionLoader(Protocol.class).hasExtension(providerUrl.getProtocol())) {
                logger.error(new IllegalStateException("Unsupported protocol " + providerUrl.getProtocol() + " in notified url: " + providerUrl + " from registry " + getUrl().getAddress() + " to consumer " + NetUtils.getLocalHost() 
                        + ", supported protocol: "+ExtensionLoader.getExtensionLoader(Protocol.class).getSupportedExtensions()));
                continue;
            }
            //zk注册中心获取的当前接口下的提供者
            // providerUrl=dubbo://192.168.213.1:20881/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&dubbo=2.0.0&generic=false&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&loadbalance=roundrobin&methods=sayHello,gude&owner=william&pid=13968&revision=10.11.11&side=provider&timestamp=1527396811779&version=10.11.11
            //dubbo接口有些配置参数既可以配置在服务提供者、也可以配置在消费端，2者进行合并
            URL url = mergeUrl(providerUrl);
            
            String key = url.toFullString(); // URL参数是排序的
            if (keys.contains(key)) { // 重复URL
                continue;
            }
            keys.add(key);
            // 缓存key为没有合并消费端参数的URL，不管消费端如何合并参数，如果服务端URL发生变化，则重新refer
            Map<String, Invoker<T>> localUrlInvokerMap = this.urlInvokerMap; // local reference
            Invoker<T> invoker = localUrlInvokerMap == null ? null : localUrlInvokerMap.get(key);
            if (invoker == null) { // 缓存中没有，重新refer
                try {
                	boolean enabled = true;
                	if (url.hasParameter(Constants.DISABLED_KEY)) {
                		enabled = ! url.getParameter(Constants.DISABLED_KEY, false);
                	} else {
                		enabled = url.getParameter(Constants.ENABLED_KEY, true);
                	}
                	if (enabled) {
                	    //核心  会创建到服务端的连接  protocol为Protocol$Adpative
                		invoker = new InvokerDelegete<T>(protocol.refer(serviceType, url), url, providerUrl);
                	}
                } catch (Throwable t) {
                    logger.error("Failed to refer invoker for interface:"+serviceType+",url:("+url+")" + t.getMessage(), t);
                }
                if (invoker != null) { // 将新的引用放入缓存
                    newUrlInvokerMap.put(key, invoker);
                }
            }else {
                newUrlInvokerMap.put(key, invoker);
            }
        }
        keys.clear();
        return newUrlInvokerMap;
    }
    
}   
```

protocol.refer(serviceType, url)->Protocol$Adpative（获取DubboProtocol的包装）-> ProtocolListenerWrapper->ListenerInvokerWrapper->ProtocolFilterWrapper（创建Invoker Filter链,DubboInvoker在链条最后）->DubboProtocol(创建DubboInvoker)

######  创建客户端连接

创建客户端连接并返回DubboInvoker

```java
public class DubboProtocol extends AbstractProtocol {
    public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
        //dubbo://192.168.213.1:20881/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-consumer&check=false&dubbo=2.0.0&generic=false&group=guodong&interface=com.alibaba.dubbo.demo.DemoService&loadbalance=roundrobin&methods=sayHello,gude&owner=william&pid=18396&revision=10.11.11&side=consumer&timeout=60000&timestamp=1527402348773&version=10.11.11
        //核心 创建到服务端连接 create rpc invoker.
        DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);
        return invoker;
    }
     private ExchangeClient[] getClients(URL url){
        //是否共享连接
        boolean service_share_connect = false;
        int connections = url.getParameter(Constants.CONNECTIONS_KEY, 0);
        //如果connections不配置，则共享连接，否则每服务每连接
        if (connections == 0){
            service_share_connect = true;
            connections = 1;
        }
        
        ExchangeClient[] clients = new ExchangeClient[connections];
        for (int i = 0; i < clients.length; i++) {
            if (service_share_connect){
                clients[i] = getSharedClient(url);
            } else {
                clients[i] = initClient(url);
            }
        }
        return clients;
    }
    
     
    /**
     *获取共享连接 
     */
    private ExchangeClient getSharedClient(URL url){
        String key = url.getAddress();
        //先去缓存查找客户端
        ReferenceCountExchangeClient client = referenceClientMap.get(key);
        if ( client != null ){
            if ( !client.isClosed()){
                client.incrementAndGetCount();
                return client;
            } else {
//                logger.warn(new IllegalStateException("client is closed,but stay in clientmap .client :"+ client));
                referenceClientMap.remove(key);
            }
        }
        ExchangeClient exchagneclient = initClient(url);
        //包装为ReferenceCountExchangeClient 加入缓存
        client = new ReferenceCountExchangeClient(exchagneclient, ghostClientMap);
        referenceClientMap.put(key, client);
        ghostClientMap.remove(key);
        return client; 
    }
 /**
     * 创建新连接.
     */
    private ExchangeClient initClient(URL url) {
        
        // client type setting. 默认是netty3
        String str = url.getParameter(Constants.CLIENT_KEY, url.getParameter(Constants.SERVER_KEY, Constants.DEFAULT_REMOTING_CLIENT));

        String version = url.getParameter(Constants.DUBBO_VERSION_KEY);
        boolean compatible = (version != null && version.startsWith("1.0."));
        //设置编解码器
        url = url.addParameter(Constants.CODEC_KEY, Version.isCompatibleVersion() && compatible ? COMPATIBLE_CODEC_NAME : DubboCodec.NAME);
        //默认开启heartbeat
        url = url.addParameterIfAbsent(Constants.HEARTBEAT_KEY, String.valueOf(Constants.DEFAULT_HEARTBEAT));
        
        // BIO存在严重性能问题，暂时不允许使用
        if (str != null && str.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
            throw new RpcException("Unsupported client type: " + str + "," +
                    " supported client type is " + StringUtils.join(ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions(), " "));
        }
        
        ExchangeClient client ;
        try {
            //设置连接应该是lazy的 默认是马上连接不上lazy
            if (url.getParameter(Constants.LAZY_CONNECT_KEY, false)){
                client = new LazyConnectExchangeClient(url ,requestHandler);
            } else {
                //服务端建立连接
                client = Exchangers.connect(url ,requestHandler);
            }
        } catch (RemotingException e) {
            throw new RpcException("Fail to create remoting client for service(" + url
                    + "): " + e.getMessage(), e);
        }
        return client;
    }
    
}
```

建立连接：Exchangers->HeaderExchanger->Transporters

```java
public class Exchangers { 
    public static ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
            if (url == null) {
                throw new IllegalArgumentException("url == null");
            }
            if (handler == null) {
                throw new IllegalArgumentException("handler == null");
            }
            url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
            return getExchanger(url).connect(url, handler);
        }
}
```

```java
public class HeaderExchanger implements Exchanger {
    
    public static final String NAME = "header";

    public ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
        //对返回的NettyClient进行包装 这里会启动发送定时心跳 60s一次
        return new HeaderExchangeClient(Transporters.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }
}
```



```java
public class Transporters {  
    public static Client connect(URL url, ChannelHandler... handlers) throws RemotingException {
            if (url == null) {
                throw new IllegalArgumentException("url == null");
            }
            ChannelHandler handler;
            if (handlers == null || handlers.length == 0) {
                handler = new ChannelHandlerAdapter();
            } else if (handlers.length == 1) {
                handler = handlers[0];
            } else {
                handler = new ChannelHandlerDispatcher(handlers);
            }
        	//getTransporter()为Transporter$Adpative，默认扩展为netty
            return getTransporter().connect(url, handler);
        }
}
```

```java
public class NettyTransporter implements Transporter {

    public static final String NAME = "netty";
    
    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }

    public Client connect(URL url, ChannelHandler listener) throws RemotingException {
       	//创建nettyClient 会启动状态检测定时器 断开会进行重连 默认间隔2s
        return new NettyClient(url, listener);
    }

}
```

最终获取到ReferenceCountExchangeClient(有缓存直接从缓存中获取，没有新建一个客户端，这里要注意是否一个提供者所有接口都共享一个客户端，默认情况是共享一个客户端)，然后创建DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);

#### 加入集群路由

默认集群策略：failover

 Cluster$Adpative->MockClusterWrapper->MockClusterInvoker->FailoverCluster

```java
public class FailoverCluster implements Cluster {

    public final static String NAME = "failover";

    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        return new FailoverClusterInvoker<T>(directory);
    }
}
```

最终返回包装后的FailoverClusterInvoker

### 创建invoker代理

根据 返回的FailoverClusterInvoker创建代理，最终会创建DemoService接口的代理类.

proxyFactory.getProxy(invoker)->ProxyFactory$Adpative->StubProxyFactoryWrapper->AbstractProxyFactory->JavassistProxyFactory

```java
public class ProxyFactory$Adpative implements com.alibaba.dubbo.rpc.ProxyFactory {
    public java.lang.Object getProxy(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null)
            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
        com.alibaba.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("proxy", "javassist");
        //默认情况下使用javassist
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
        com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getProxy(arg0);
    }

    public com.alibaba.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, com.alibaba.dubbo.common.URL arg2) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg2 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg2;
        String extName = url.getParameter("proxy", "javassist");
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
        com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getInvoker(arg0, arg1, arg2);
    }
}
```

```java
public abstract class AbstractProxyFactory implements ProxyFactory {

    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        Class<?>[] interfaces = null;
        String config = invoker.getUrl().getParameter("interfaces");
        if (config != null && config.length() > 0) {
            String[] types = Constants.COMMA_SPLIT_PATTERN.split(config);
            if (types != null && types.length > 0) {
                interfaces = new Class<?>[types.length + 2];
                interfaces[0] = invoker.getInterface();
                interfaces[1] = EchoService.class;
                for (int i = 0; i < types.length; i ++) {
                    interfaces[i + 1] = ReflectUtils.forName(types[i]);
                }
            }
        }
        if (interfaces == null) {
            interfaces = new Class<?>[] {invoker.getInterface(), EchoService.class};
        }
        return getProxy(invoker, interfaces);
    }
    
    public abstract <T> T getProxy(Invoker<T> invoker, Class<?>[] types);

}
```

```java
public class JavassistProxyFactory extends AbstractProxyFactory {

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper类不能正确处理带$的类名
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

}
```

主要核心是Proxy.getProxy(interfaces) 产生代理类。

####  原理

Proxy类的newInstance(InvocationHandler handler)方法是一个抽象方法，所有Proxy.getProxy(interfaces) 要先产生Proxy的代理类，实现newInstance(InvocationHandler handler)方法。

##### Proxy例子

```java
public interface Gude {
    void setName(String name);
    String getName();
}
```

Proxy会根据interfaces实现抽象方法产生代理类,产生的类名 序号会递增 从0开始 Proxy0 1 2 3... 和proxy0 1 2...对应

- Gude接口生成的Proxy代理类

```java
public class Proxy0 extends Proxy {

    public Object newInstance(InvocationHandler h) {
        //  return new com.alibaba.dubbo.common.bytecode.proxy0($1); proxy0和接口实现类对应
        return new com.alibaba.dubbo.common.bytecode.proxy0(h);
    }
}
```

- Gude接口实现类

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
       // args[0] = ($w) $1;
        args[0] = (Object) arg0;
        Object ret = handler.invoke(this, methods[1], args);
    }
}
```

***



所以JavassistProxyFactory：

```java
public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
    //默认interfaces有2个
    //interface com.alibaba.dubbo.rpc.service.EchoService 默认每个提供接口产生代理类也会去实现这个接口
    //interface com.alibaba.dubbo.demo.DemoService
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
}
```

最终会产生DemoService、EchoService的实现类proxy0(ps:不一定是序号0根据顺序递增)并返回。这个代理类会作为bean注册到spring容器中。在业务中调用此接口的时候，会走proxy0的方法，这里会走集群容错、路由等去远程调用（调用的是返回的MockClusterInvoker->FailoverClusterInvoker....）。

## 总结

过程：获取注册中心->注册consumer到注册中心->订阅providers服务提供者->连接到提供者->创建接口代理。(ps:都是基于单个接口，每个接口都会经过这个流程，只不过有些可以直接去缓存获取，比如共享客户端已经创建过连接了)