---
title: 学习dubbo源码-泛化调用
date: 2018-11-17 14:39:19
tags: [dubbo]
---

公司支付网关目前使用了dubbo泛化调用.学习了下dubbo的泛化调用原理。
我们知道dubbo最终调用的的是`DubboInvoker#doInvoker` (ps:dubbo协议)

```java
    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
        inv.setAttachment(Constants.VERSION_KEY, version);
        
        ExchangeClient currentClient;
        if (clients.length == 1) {
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
            boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY,Constants.DEFAULT_TIMEOUT);
            if (isOneway) {
            	boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                currentClient.send(inv, isSent);
                RpcContext.getContext().setFuture(null);
                return new RpcResult();
            } else if (isAsync) {
            	ResponseFuture future = currentClient.request(inv, timeout) ;
                RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
                return new RpcResult();
            } else {
            	RpcContext.getContext().setFuture(null);
                return (Result) currentClient.request(inv, timeout).get();
            }
        } catch (TimeoutException e) {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
```

<!--more-->

dubbo发送是RpcInvocation，不管是正常调用还是泛化调用，它的关注点就是
```java
public class RpcInvocation implements Invocation, Serializable {

    private static final long serialVersionUID = -4355285085441097045L;
    //调用方法名
    private String               methodName;
    //调用参数类型
    private Class<?>[]           parameterTypes;
    //参数值
    private Object[]             arguments;
    //扩展附加参数，如：调用接口、是否泛化调用、版本等
    private Map<String, String>  attachments;
}
```
所以，泛化调用时可以不需要将发布的接口架包放到classpath下，我们只需通过组装参数就能进行调用，所以在支付网关建设时，所有内部接口都通过控制台动态配置到支付网关系统给三方业务使用，网关作为所有接口的入口，无需引入接口架包。这里需要的注意点是，虽然我们配置了泛化调用，但是最终引用远程服务时，还是引用真正调用的接口，就是例子中`setInterface`接口。
* 泛化官网调用例子：
```java
// 引用远程服务 
// 该实例很重量，里面封装了所有与注册中心及服务提供方连接，请缓存
ReferenceConfig<GenericService> reference = new ReferenceConfig<GenericService>(); 
// 弱类型接口名
reference.setInterface("com.xxx.XxxService");  
reference.setVersion("1.0.0");
// 声明为泛化接口 
reference.setGeneric(true);  

// 用com.alibaba.dubbo.rpc.service.GenericService可以替代所有接口引用  
GenericService genericService = reference.get(); 
 
// 基本类型以及Date,List,Map等不需要转换，直接调用 
Object result = genericService.$invoke("sayHello", new String[] {"java.lang.String"}, new Object[] {"world"}); 

```
## <a name="isyufs"></a>实现细节
### <a name="1ryxli"></a>消费者
网关主要是使用了消费端泛化实现，这里主要做了相关场景参数转换，代码在 `GenericImplFilter` 
```java
 public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String generic = invoker.getUrl().getParameter(Constants.GENERIC_KEY);
        //服务端是泛化暴露，客户端不是使用泛化调用场景
        if (ProtocolUtils.isGeneric(generic)
                && ! Constants.$INVOKE.equals(invocation.getMethodName())
                && invocation instanceof RpcInvocation) {
            RpcInvocation invocation2 = (RpcInvocation) invocation;
            String methodName = invocation2.getMethodName();
            Class<?>[] parameterTypes = invocation2.getParameterTypes();
            Object[] arguments = invocation2.getArguments();
            
            String[] types = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i ++) {
                types[i] = ReflectUtils.getName(parameterTypes[i]);
            }

            Object[] args;
            if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                args = new Object[arguments.length];
                for(int i = 0; i < arguments.length; i++) {
                    args[i] = JavaBeanSerializeUtil.serialize(arguments[i], JavaBeanAccessor.METHOD);
                }
            } else {
                args = PojoUtils.generalize(arguments);
            }
            
            invocation2.setMethodName(Constants.$INVOKE);
            invocation2.setParameterTypes(GENERIC_PARAMETER_TYPES);
            invocation2.setArguments(new Object[] {methodName, types, args});
            Result result = invoker.invoke(invocation2);
            
            if (! result.hasException()) {
                Object value = result.getValue();
                try {
                    Method method = invoker.getInterface().getMethod(methodName, parameterTypes);
                    if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                        if (value == null) {
                            return new RpcResult(value);
                        } else if (value instanceof JavaBeanDescriptor) {
                            return new RpcResult(JavaBeanSerializeUtil.deserialize((JavaBeanDescriptor)value));
                        } else {
                            throw new RpcException(
                                new StringBuilder(64)
                                    .append("The type of result value is ")
                                    .append(value.getClass().getName())
                                    .append(" other than ")
                                    .append(JavaBeanDescriptor.class.getName())
                                    .append(", and the result is ")
                                    .append(value).toString());
                        }
                    } else {
                        return new RpcResult(PojoUtils.realize(value, method.getReturnType(), method.getGenericReturnType()));
                    }
                } catch (NoSuchMethodException e) {
                    throw new RpcException(e.getMessage(), e);
                }
            } else if (result.getException() instanceof GenericException) {
                GenericException exception = (GenericException) result.getException();
                try {
                    String className = exception.getExceptionClass();
                    Class<?> clazz = ReflectUtils.forName(className);
                    Throwable targetException = null;
                    Throwable lastException = null;
                    try {
                        targetException = (Throwable) clazz.newInstance();
                    } catch (Throwable e) {
                        lastException = e;
                        for (Constructor<?> constructor : clazz.getConstructors()) {
                            try {
                                targetException = (Throwable) constructor.newInstance(new Object[constructor.getParameterTypes().length]);
                                break;
                            } catch (Throwable e1) {
                                lastException = e1;
                            }
                        }
                    }
                    if (targetException != null) {
                        try {
                            Field field = Throwable.class.getDeclaredField("detailMessage");
                            if (! field.isAccessible()) {
                                field.setAccessible(true);
                            }
                            field.set(targetException, exception.getExceptionMessage());
                        } catch (Throwable e) {
                            logger.warn(e.getMessage(), e);
                        }
                        result = new RpcResult(targetException);
                    } else if (lastException != null) {
                        throw lastException;
                    }
                } catch (Throwable e) {
                    throw new RpcException("Can not deserialize exception " + exception.getExceptionClass() + ", message: " + exception.getExceptionMessage(), e);
                }
            }
            return result;
        }
        //服务端非泛化暴露，消费使用泛化调用
        if (invocation.getMethodName().equals(Constants.$INVOKE)
            && invocation.getArguments() != null
            && invocation.getArguments().length == 3
            && ProtocolUtils.isGeneric(generic)) {

            Object[] args = (Object[]) invocation.getArguments()[2];
            if (ProtocolUtils.isJavaGenericSerialization(generic)) {

                for (Object arg : args) {
                    if (!(byte[].class == arg.getClass())) {
                        error(byte[].class.getName(), arg.getClass().getName());
                    }
                }
            } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                for(Object arg : args) {
                    if (!(arg instanceof JavaBeanDescriptor)) {
                        error(JavaBeanDescriptor.class.getName(), arg.getClass().getName());
                    }
                }
            }

            ((RpcInvocation)invocation).setAttachment(
                Constants.GENERIC_KEY, invoker.getUrl().getParameter(Constants.GENERIC_KEY));
        }
        return invoker.invoke(invocation);
    }

```
### <a name="bkqpog"></a>提供者
提供者泛化支持类 `GenericFilter` ，通常用于Mock，服务端泛化用到的地方不多。
```java
public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
        //用于用户自己定义的方法和com.alibaba.dubbo.rpc.service.GenericService方法一样的判断，并且是非泛化
        if (inv.getMethodName().equals(Constants.$INVOKE) 
                && inv.getArguments() != null
                && inv.getArguments().length == 3
                && ! ProtocolUtils.isGeneric(invoker.getUrl().getParameter(Constants.GENERIC_KEY))) {
            String name = ((String) inv.getArguments()[0]).trim();
            String[] types = (String[]) inv.getArguments()[1];
            Object[] args = (Object[]) inv.getArguments()[2];
            try {
                 //查找当前接口满足条件的方法
                Method method = ReflectUtils.findMethodByMethodSignature(invoker.getInterface(), name, types);
                Class<?>[] params = method.getParameterTypes();
                if (args == null) {
                    args = new Object[params.length];
                }
                String generic = inv.getAttachment(Constants.GENERIC_KEY);
                if (StringUtils.isEmpty(generic)
                    || ProtocolUtils.isDefaultGenericSerialization(generic)) {
                    args = PojoUtils.realize(args, params, method.getGenericParameterTypes());
                } else if (ProtocolUtils.isJavaGenericSerialization(generic)) {
                    for(int i = 0; i < args.length; i++) {
                        if (byte[].class == args[i].getClass()) {
                            try {
                                UnsafeByteArrayInputStream is = new UnsafeByteArrayInputStream((byte[])args[i]);
                                args[i] = ExtensionLoader.getExtensionLoader(Serialization.class)
                                    .getExtension(Constants.GENERIC_SERIALIZATION_NATIVE_JAVA)
                                    .deserialize(null, is).readObject();
                            } catch (Exception e) {
                                throw new RpcException("Deserialize argument [" + (i + 1) + "] failed.", e);
                            }
                        } else {
                            throw new RpcException(
                                new StringBuilder(32).append("Generic serialization [")
                                    .append(Constants.GENERIC_SERIALIZATION_NATIVE_JAVA)
                                    .append("] only support message type ")
                                    .append(byte[].class)
                                    .append(" and your message type is ")
                                    .append(args[i].getClass()).toString());
                        }
                    }
                } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                    for(int i = 0; i < args.length; i++) {
                        if (args[i] instanceof JavaBeanDescriptor) {
                            args[i] = JavaBeanSerializeUtil.deserialize((JavaBeanDescriptor)args[i]);
                        } else {
                            throw new RpcException(
                                new StringBuilder(32)
                                    .append("Generic serialization [")
                                    .append(Constants.GENERIC_SERIALIZATION_BEAN)
                                    .append("] only support message type ")
                                    .append(JavaBeanDescriptor.class.getName())
                                    .append(" and your message type is ")
                                    .append(args[i].getClass().getName()).toString());
                        }
                    }
                }
                Result result = invoker.invoke(new RpcInvocation(method, args, inv.getAttachments()));
                if (result.hasException()
                        && ! (result.getException() instanceof GenericException)) {
                    return new RpcResult(new GenericException(result.getException()));
                }
                if (ProtocolUtils.isJavaGenericSerialization(generic)) {
                    try {
                        UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(512);
                        ExtensionLoader.getExtensionLoader(Serialization.class)
                            .getExtension(Constants.GENERIC_SERIALIZATION_NATIVE_JAVA)
                            .serialize(null, os).writeObject(result.getValue());
                        return new RpcResult(os.toByteArray());
                    } catch (IOException e) {
                        throw new RpcException("Serialize result failed.", e);
                    }
                } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                    return new RpcResult(JavaBeanSerializeUtil.serialize(result.getValue(), JavaBeanAccessor.METHOD));
                } else {
                    //POJO->HashMap
                    return new RpcResult(PojoUtils.generalize(result.getValue()));
                }
            } catch (NoSuchMethodException e) {
                throw new RpcException(e.getMessage(), e);
            } catch (ClassNotFoundException e) {
                throw new RpcException(e.getMessage(), e);
            }
        }
        return invoker.invoke(inv);
    }
```
举个进入上面条件判断的场景例子
```java
public interface HelloService {
    Object $invoke(String method, String[] parameterTypes, Object[] args);

    String hello(String world);
}
```
这个接口非泛化暴露时就会进入上面的判断逻辑，`$invoke` 的参数用于查找当前接口的方法,如`hello` ,逻辑代码就是这行`ReflectUtils.findMethodByMethodSignature(invoker.getInterface(), name, types)`

#### <a name="vzrngz"></a>PojoUtils
* generalize 泛化调用消费方可能没有包含该返回对象架包，所以将POJO转化成HashMap


![image.png | left | 747x537](https://gitee.com/zhangguodong/image/raw/master/null/1545287534084-0039e4f2-5809-4c1b-85d4-24f1e8c910d8.png "")


