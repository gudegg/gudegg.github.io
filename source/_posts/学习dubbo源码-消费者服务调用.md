---
title: 学习dubbo源码-消费者服务调用
date: 2018-05-28 20:39:10
tags: dubbo
---

```java
DemoService demoService = (DemoService) context.getBean("demoService");
demoService.sayHello("world");
```

由上一节分析知道调用demoService实际上是返回的proxy0(不一定是0)代理类，由于proxy0源码没有，所以debug会直接进InvokerInvocationHandler


InvokerInvocationHandler.invoke:
```java
public class InvokerInvocationHandler implements InvocationHandler {

    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //proxy=proxy0 代理类本身 method:调用方法 args:方法参数
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        //invoker=MockClusterInvoker
        return invoker.invoke(new RpcInvocation(method, args)).recreate();
    }

}
```

<!--more-->

```java
public class RpcInvocation implements Invocation, Serializable {

    private static final long serialVersionUID = -4355285085441097045L;
    //调用方法名
    private String methodName;
    //方法参数类型
    private Class<?>[] parameterTypes;
    //方法参数值
    private Object[] arguments;
    //额外参数，走到DubboInvoker 会添加interface=com.alibaba.dubbo.demo.DemoService
    private Map<String, String> attachments;
    // ConsumerContextFilter会设置进Invoker
    private transient Invoker<?> invoker;
}
```

MockClusterInvoker.invoke:
```java
public class MockClusterInvoker<T> implements Invoker<T> {
    public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
        //服务降级 可以通过dubbo-admin对相关接口进行配置mock
        String value = directory.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, Boolean.FALSE.toString()).trim();
        if (value.length() == 0 || value.equalsIgnoreCase("false")) {
            //no mock 默认走这里 invoker=FailoverClusterInvoker
            result = this.invoker.invoke(invocation);
        } else if (value.startsWith("force")) {
            if (logger.isWarnEnabled()) {
                logger.info("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + directory.getUrl());
            }
            //force:direct mock
            result = doMockInvoke(invocation, null);
        } else {
            //fail-mock
            try {
                result = this.invoker.invoke(invocation);
            } catch (RpcException e) {
                if (e.isBiz()) {
                    throw e;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.info("fail-mock: " + invocation.getMethodName() + " fail-mock enabled , url : " + directory.getUrl(), e);
                    }
                    result = doMockInvoke(invocation, e);
                }
            }
        }
        return result;
    }
}    
```

AbstractClusterInvoker：
```java
public abstract class AbstractClusterInvoker<T> implements Invoker<T> {
     public Result invoke(final Invocation invocation) throws RpcException {
            //判断提供者是否已经销毁
            checkWhetherDestroyed();
    
            LoadBalance loadbalance;
            //从RegistryDirectory的methodInvokerMap中获取所有Invoker ,还会经过路由规则进行过滤
            List<Invoker<T>> invokers = list(invocation);
            //默认负载均衡策略为随机权重
            if (invokers != null && invokers.size() > 0) {
                loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                        .getMethodParameter(invocation.getMethodName(), Constants.LOADBALANCE_KEY, Constants.DEFAULT_LOADBALANCE));
            } else {
                loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(Constants.DEFAULT_LOADBALANCE);
            }
            RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
            //调用FailoverClusterInvoker
            return doInvoke(invocation, invokers, loadbalance);
        }
        

    
    
     /**
     * 使用loadbalance选择invoker.</br>
     * a)先lb选择，如果在selected列表中 或者 不可用且做检验时，进入下一步(重选),否则直接返回</br>
     * b)重选验证规则：selected > available .保证重选出的结果尽量不在select中，并且是可用的
     *
     * @param availablecheck 如果设置true，在选择的时候先选invoker.available == true
     * @param selected       已选过的invoker.注意：输入保证不重复
     */
    protected Invoker<T> select(LoadBalance loadbalance, Invocation invocation, List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        if (invokers == null || invokers.size() == 0)
            return null;
        String methodName = invocation == null ? "" : invocation.getMethodName();

        boolean sticky = invokers.get(0).getUrl().getMethodParameter(methodName, Constants.CLUSTER_STICKY_KEY, Constants.DEFAULT_CLUSTER_STICKY);
        {
            //ignore overloaded method
            if (stickyInvoker != null && !invokers.contains(stickyInvoker)) {
                stickyInvoker = null;
            }
            //ignore cucurrent problem
            if (sticky && stickyInvoker != null && (selected == null || !selected.contains(stickyInvoker))) {
                if (availablecheck && stickyInvoker.isAvailable()) {
                    return stickyInvoker;
                }
            }
        }
        //选择一个调用Invoker
        Invoker<T> invoker = doselect(loadbalance, invocation, invokers, selected);

        if (sticky) {
            stickyInvoker = invoker;
        }
        return invoker;
    }
 private Invoker<T> doselect(LoadBalance loadbalance, Invocation invocation, List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        if (invokers == null || invokers.size() == 0)
            return null;
        if (invokers.size() == 1)
            return invokers.get(0);
        // 如果只有两个invoker，退化成轮循 ps:新版本已经移除这个 还是会走下面负载均衡
        if (invokers.size() == 2 && selected != null && selected.size() > 0) {
            return selected.get(0) == invokers.get(0) ? invokers.get(1) : invokers.get(0);
        }
        Invoker<T> invoker = loadbalance.select(invokers, getUrl(), invocation);

        //如果 selected中包含（优先判断） 或者 不可用&&availablecheck=true 则重试.
        if ((selected != null && selected.contains(invoker))
                || (!invoker.isAvailable() && getUrl() != null && availablecheck)) {
            try {
                Invoker<T> rinvoker = reselect(loadbalance, invocation, invokers, selected, availablecheck);
                if (rinvoker != null) {
                    invoker = rinvoker;
                } else {
                    //看下第一次选的位置，如果不是最后，选+1位置.
                    int index = invokers.indexOf(invoker);
                    try {
                        //最后在避免碰撞
                        invoker = index < invokers.size() - 1 ? invokers.get(index + 1) : invoker;
                    } catch (Exception e) {
                        logger.warn(e.getMessage() + " may because invokers list dynamic change, ignore.", e);
                    }
                }
            } catch (Throwable t) {
                logger.error("clustor relselect fail reason is :" + t.getMessage() + " if can not slove ,you can set cluster.availablecheck=false in url", t);
            }
        }
        return invoker;
    }
}        
```

FailoverClusterInvoker：

```java
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(FailoverClusterInvoker.class);

    public FailoverClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyinvokers = invokers;
        checkInvokers(copyinvokers, invocation);
        //默认3 即重试2次
        int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //重试时，进行重新选择，避免重试时invoker列表已发生变化.
            //注意：如果列表发生了变化，那么invoked判断会失效，因为invoker示例已经改变
            if (i > 0) {
                checkWhetherDestroyed();
                copyinvokers = list(invocation);
                //重新检查一下
                checkInvokers(copyinvokers, invocation);
            }
            //AbstractClusterInvoker 进入负载均衡 缺省为 random 随机调用
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            invoked.add(invoker);
            //RpcContext使用ThreadLocal<RpcContext> LOCAL = new ThreadLocal<RpcContext>()。保存了当前调用线程的相关信息
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                /**
                *-->InvokerWrapper.invoke
                *  -->ProtocolFilterWrapper.invoke
                *    -->ConsumerContextFilter.invoke //设置LocalAddress、RemoteAddress、Invocation、Invoker等参数到RpcContext
                *      -->ProtocolFilterWrapper.invoke
                *        -->FutureFilter.invoke //同步异步判断，异步回调
                *          -->ProtocolFilterWrapper.invoke
                *            -->MonitorFilter.invoke //判断是否启用监控，记录调用开始时间、并发数
                *              -->ListenerInvokerWrapper.invoke //进入DubboInvoker 
                *               -->AbstractInvoker.invoke //DubboInvoker继承了AbstractInvoker
                **/
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                    logger.warn("Although retry the method " + invocation.getMethodName()
                            + " in the service " + getInterface().getName()
                            + " was successful by the provider " + invoker.getUrl().getAddress()
                            + ", but there have been failed providers " + providers
                            + " (" + providers.size() + "/" + copyinvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost()
                            + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                            + le.getMessage(), le);
                }
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
        throw new RpcException(le != null ? le.getCode() : 0, "Failed to invoke the method "
                + invocation.getMethodName() + " in the service " + getInterface().getName()
                + ". Tried " + len + " times of the providers " + providers
                + " (" + providers.size() + "/" + copyinvokers.size()
                + ") from the registry " + directory.getUrl().getAddress()
                + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                + Version.getVersion() + ". Last error is: "
                + (le != null ? le.getMessage() : ""), le != null && le.getCause() != null ? le.getCause() : le);
    }

}
```


AbstractInvoker:
```java
public abstract class AbstractInvoker<T> implements Invoker<T> {
     private final Map<String, String> attachment;

     public Result invoke(Invocation inv) throws RpcException {
        if (destroyed.get()) {
            throw new RpcException("Rpc invoker for service " + this + " on consumer " + NetUtils.getLocalHost()
                    + " use dubbo version " + Version.getVersion()
                    + " is DESTROYED, can not be invoked any more!");
        }
        RpcInvocation invocation = (RpcInvocation) inv;
        invocation.setInvoker(this);
        if (attachment != null && attachment.size() > 0) {
        //添加interface=com.alibaba.dubbo.demo.DemoService
            invocation.addAttachmentsIfAbsent(attachment);
        }
        Map<String, String> context = RpcContext.getContext().getAttachments();
        if (context != null) {
            invocation.addAttachmentsIfAbsent(context);
        }
        if (getUrl().getMethodParameter(invocation.getMethodName(), Constants.ASYNC_KEY, false)) {
            invocation.setAttachment(Constants.ASYNC_KEY, Boolean.TRUE.toString());
        }
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);


        try {
            return doInvoke(invocation);
        } catch (InvocationTargetException e) { // biz exception
            Throwable te = e.getTargetException();
            if (te == null) {
                return new RpcResult(e);
            } else {
                if (te instanceof RpcException) {
                    ((RpcException) te).setCode(RpcException.BIZ_EXCEPTION);
                }
                return new RpcResult(te);
            }
        } catch (RpcException e) {
            if (e.isBiz()) {
                return new RpcResult(e);
            } else {
                throw e;
            }
        } catch (Throwable e) {
            return new RpcResult(e);
        }
    }

    protected abstract Result doInvoke(Invocation invocation) throws Throwable;
}
```

```java
public class DubboInvoker<T> extends AbstractInvoker<T> {

    private final ExchangeClient[] clients;

    private final AtomicPositiveInteger index = new AtomicPositiveInteger();

    private final String version;

    private final ReentrantLock destroyLock = new ReentrantLock();

    private final Set<Invoker<?>> invokers;



    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        //"path" -> "com.alibaba.dubbo.demo.DemoService"
        inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
        //"version" -> "0.0.0"
        inv.setAttachment(Constants.VERSION_KEY, version);

        ExchangeClient currentClient;
        if (clients.length == 1) {
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
        //同步异步
            boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
            //是否需要返回结果 即单向通讯
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
            //单向发送
            if (isOneway) {
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                currentClient.send(inv, isSent);
                RpcContext.getContext().setFuture(null);
                return new RpcResult();
            } else if (isAsync) {
                ResponseFuture future = currentClient.request(inv, timeout);
                RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
                return new RpcResult();
            } else {
                //同步需要返回结果发送 异步变同步  currentClient.request(inv, timeout) 返回DefaultFuture
                /**
                *-->ReferenceCountExchangeClient.request 啥都不干直接调用下一个
                *---> HeaderExchangeClient.request 啥都不干直接调用下一个
                *----> HeaderExchangeChannel.request
                **/
                RpcContext.getContext().setFuture(null);
                return (Result) currentClient.request(inv, timeout).get();
            }
        } catch (TimeoutException e) {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!super.isAvailable())
            return false;
        for (ExchangeClient client : clients) {
            if (client.isConnected() && !client.hasAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY)) {
                //cannot write == not Available ?
                return true;
            }
        }
        return false;
    }

}
```
此时doInvoke的inv
![image](/images/rpcInvocation.png)


HeaderExchangeChannel:
```java
 public ResponseFuture request(Object request, int timeout) throws RemotingException {
        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send request " + request + ", cause: The channel " + this + " is closed!");
        }
        // create request.  会产生此次请求的唯一ID
        Request req = new Request();
        //这里dubbo写死了 不是很优雅
        req.setVersion("2.0.0");
        req.setTwoWay(true);
        req.setData(request);
        //非常重要
        DefaultFuture future = new DefaultFuture(channel, req, timeout);
        try {
            //具体的通讯协议发送 ：默认为netty3 异步发送
            //channel=NettyClient。
            /**
            *-->AbstractPeer
            *--->AbstractClient
            *---->NettyChannel 真正的netty发送,会对发送的消息进行编码
            **/
            channel.send(req);
        } catch (RemotingException e) {
            future.cancel();
            throw e;
        }
        return future;
    }

```
```java
public class Request {

    public static final String HEARTBEAT_EVENT = null;

    public static final String READONLY_EVENT = "R";

    private static final AtomicLong INVOKE_ID = new AtomicLong(0);
    public Request() {
        //产生唯一ID
        mId = newId();
    }
    private static long newId() {
        // getAndIncrement()增长到MAX_VALUE时，再增长会变为MIN_VALUE，负数也可以做为ID
        return INVOKE_ID.getAndIncrement();
    }
    public long getId() {
        return mId;
    }
}    
```

### 异步变同步

看下new DefaultFuture(channel, req, timeout); 

```java
public class DefaultFuture implements ResponseFuture {
 public DefaultFuture(Channel channel, Request request, int timeout) {
        this.channel = channel;
        this.request = request;
        //每次发送的唯一ID 根据这个ID唤醒等待的调用请求,也就是异步变同步的关键
        this.id = request.getId();
        this.timeout = timeout > 0 ? timeout : channel.getUrl().getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
        // put into waiting map. 通过id找到本次调用DefaultFuture，以此唤醒此次调用继续 或者超时抛出异常
        FUTURES.put(id, this);
        CHANNELS.put(id, channel);
    }
    
    public Object get() throws RemotingException {
        return get(timeout);
    }

    public Object get(int timeout) throws RemotingException {
        if (timeout <= 0) {
            timeout = Constants.DEFAULT_TIMEOUT;
        }
        if (!isDone()) {
            long start = System.currentTimeMillis();
            lock.lock();
            try {
                while (!isDone()) {
                    //等待， 等到提供者发回的数据来唤醒此次调用
                    done.await(timeout, TimeUnit.MILLISECONDS);
                    if (isDone() || System.currentTimeMillis() - start > timeout) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
            if (!isDone()) {
                throw new TimeoutException(sent > 0, channel, getTimeoutMessage(false));
            }
        }
        return returnFromResponse();
    }
    
    private Object returnFromResponse() throws RemotingException {
        Response res = response;
        if (res == null) {
            throw new IllegalStateException("response cannot be null");
        }
        if (res.getStatus() == Response.OK) {
            //返回服务提供者的结果
            return res.getResult();
        }
        if (res.getStatus() == Response.CLIENT_TIMEOUT || res.getStatus() == Response.SERVER_TIMEOUT) {
            throw new TimeoutException(res.getStatus() == Response.SERVER_TIMEOUT, channel, res.getErrorMessage());
        }
        throw new RemotingException(channel, res.getErrorMessage());
    }
    
    //根据服务提供者发回的数据唤醒等待的线程，这个ID(response.getId())就是发送时的ID 全局唯一
     public static void received(Channel channel, Response response) {
        try {
            DefaultFuture future = FUTURES.remove(response.getId());
            if (future != null) {
                future.doReceived(response);
            } else {
                logger.warn("The timeout response finally returned at "
                        + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                        + ", response " + response
                        + (channel == null ? "" : ", channel: " + channel.getLocalAddress()
                        + " -> " + channel.getRemoteAddress()));
            }
        } finally {
            CHANNELS.remove(response.getId());
        }
    }
    
     private void doReceived(Response res) {
        lock.lock();
        try {
            response = res;
            if (done != null) {
                //唤醒   done.await(timeout, TimeUnit.MILLISECONDS);继续往下走
                done.signal();
            }
        } finally {
            lock.unlock();
        }
        if (callback != null) {
            invokeCallback(callback);
        }
    }
}
```