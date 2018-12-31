---
title: 学习dubbo源码-编码与解码
date: 2018-05-30 20:39:10
tags: dubbo
---

## 客户端消息编码

```java
final class NettyCodecAdapter {
       @Sharable
    private class InternalEncoder extends OneToOneEncoder {

        @Override
        protected Object encode(ChannelHandlerContext ctx, Channel ch, Object msg) throws Exception {
            com.alibaba.dubbo.remoting.buffer.ChannelBuffer buffer =
                    com.alibaba.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(1024);
            NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
            try {
                /**
                *->DubboCountCodec //编码啥都不干 调用下一个编码器
                *-->ExchangeCodec
                *--->DubboCodec
                **/
                codec.encode(channel, buffer, msg);
            } finally {
                NettyChannel.removeChannelIfDisconnected(ch);
            }
            return ChannelBuffers.wrappedBuffer(buffer.toByteBuffer());
        }
    }
}
```


![image](/images/clipboard.png)

dubbo的消息头是一个定长的 16个字节。
- 针对Request:

1. 第1-2个字节：固定魔法值：MAGIC = (short) 0xdabb

2. 第3个字节： 5位序列化方式 、1位双向(有去有回) 或单向（有去无回）、1位事件标识、1位请求request还是响应response  

3. 第4个字节：状态位，request没设置第四个字节 

4. 第5-12个字节：请求id,占用8个字节，全局唯一ID，consumer和provider的来回通信标记。

5. 第13-16个字节：消息体的长度占用4字节，消息头+请求数据的长度。\

   <!--more-->

```java
public class ExchangeCodec extends TelnetCodec {
    protected static final short MAGIC = (short) 0xdabb;
    protected static final byte MAGIC_HIGH = Bytes.short2bytes(MAGIC)[0];
    protected static final byte MAGIC_LOW = Bytes.short2bytes(MAGIC)[1];
    // message flag.
    protected static final byte FLAG_REQUEST = (byte) 0x80;
    protected static final byte FLAG_TWOWAY = (byte) 0x40;
    protected static final byte FLAG_EVENT = (byte) 0x20;
    protected static final int SERIALIZATION_MASK = 0x1f;
  public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        //Request请求编码
        if (msg instanceof Request) {
            //channel=NettyChannel buffer=com.alibaba.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(1024)动态分配的,此时没内容. msg发送的请求对象Request
            encodeRequest(channel, buffer, (Request) msg);
        
        //Response返回结果编码    
        } else if (msg instanceof Response) {
            encodeResponse(channel, buffer, (Response) msg);
        } else {
            //telnet
            super.encode(channel, buffer, msg);
        }
    }
    
     protected void encodeRequest(Channel channel, ChannelBuffer buffer, Request req) throws IOException {
        //channel持有URL对象,根据url获取序列化方式 不指定 默认hessian2
        Serialization serialization = getSerialization(channel);
        // header. 开辟消息头大小
        byte[] header = new byte[HEADER_LENGTH];
        // 协议头 魔法值(short) 0xdabb
        Bytes.short2bytes(MAGIC, header);

        // set request and serialization flag.  hessian2：getContentTypeId=2
        header[2] = (byte) (FLAG_REQUEST | serialization.getContentTypeId());

        if (req.isTwoWay()) header[2] |= FLAG_TWOWAY;
        if (req.isEvent()) header[2] |= FLAG_EVENT;

        // set request id.
        Bytes.long2bytes(req.getId(), header, 4);

        // encode request data.
        int savedWriteIndex = buffer.writerIndex();
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);
        ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
        //out=Hessian2ObjectOutput
        ObjectOutput out = serialization.serialize(channel.getUrl(), bos);
        if (req.isEvent()) {
            //事件消息
            encodeEventData(channel, out, req.getData());
        } else {
            //普通消息
            //->DubboCodec  消息体写到buffer
            encodeRequestData(channel, out, req.getData());
        }
        out.flushBuffer();
        bos.flush();
        bos.close();
        int len = bos.writtenBytes();
        //检测消息大小 默认为8m
        checkPayload(channel, len);
        //设置消息长度
        Bytes.int2bytes(len, header, 12);

        // write
        buffer.writerIndex(savedWriteIndex);
        // 消息头写到buffer
        buffer.writeBytes(header); 
        //设置写指针
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len);
    }
    
}

```

```java
public class DubboCodec extends ExchangeCodec implements Codec2{
 @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;
        
        //dubbo版本 2.0.0
        out.writeUTF(inv.getAttachment(Constants.DUBBO_VERSION_KEY, DUBBO_VERSION));
        //接口 com.alibaba.dubbo.demo.DemoService
        out.writeUTF(inv.getAttachment(Constants.PATH_KEY));
        //接口版本 默认:0.0.0
        out.writeUTF(inv.getAttachment(Constants.VERSION_KEY));
        //方法名
        out.writeUTF(inv.getMethodName());
        //方法参数类型 ILjava/lang/String;
        out.writeUTF(ReflectUtils.getDesc(inv.getParameterTypes()));
        //方法参数
        Object[] args = inv.getArguments();
        if (args != null)
            for (int i = 0; i < args.length; i++) {
                out.writeObject(encodeInvocationArgument(channel, inv, i));
            }
        out.writeObject(inv.getAttachments());
    }
}    
```

## 服务端消息解码

```java
private class InternalDecoder extends SimpleChannelUpstreamHandler {
        //用于保存不完整的包
        private com.alibaba.dubbo.remoting.buffer.ChannelBuffer buffer =
                com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
            Object o = event.getMessage();
            if (!(o instanceof ChannelBuffer)) {
                ctx.sendUpstream(event);
                return;
            }

            ChannelBuffer input = (ChannelBuffer) o;
            int readable = input.readableBytes();
            if (readable <= 0) {
                return;
            }

            com.alibaba.dubbo.remoting.buffer.ChannelBuffer message;
            //之前有存在报文和现在新来的报文拼接，然后进行拆包
            if (buffer.readable()) {
                if (buffer instanceof DynamicChannelBuffer) {
                    buffer.writeBytes(input.toByteBuffer());
                    message = buffer;
                } else {
                    int size = buffer.readableBytes() + input.readableBytes();
                    message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(
                            size > bufferSize ? size : bufferSize);
                    message.writeBytes(buffer, buffer.readableBytes());
                    message.writeBytes(input.toByteBuffer());
                }
            } else {
                //netty的ChannelBuffer转为dubbo自定义的ChannelBuffer 然后进行拆包 tcp无界存在粘包的问题
                message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.wrappedBuffer(
                        input.toByteBuffer());
            }

            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);
            Object msg;
            int saveReaderIndex;
        
            try {
                // decode object.
                do {
                    saveReaderIndex = message.readerIndex();
                    try {
                        //解码 
                        /**
                        *->DubboCountCodec 拆包
                        **/
                        msg = codec.decode(channel, message); //1
                    } catch (IOException e) {
                        //解码异常 丢掉报文
                        buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                        throw e;
                    }
                    //传给DubboCountCodec 是一个不完整的包 直接结束拆包循环
                    if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                        //2
                        message.readerIndex(saveReaderIndex);
                        break;
                    } else {
                        //相当于和开始解码的读指针一样，没有移动。但是返回结果又不是Codec2.DecodeResult.NEED_MORE_INPUT，抛出异常，不合法
                        if (saveReaderIndex == message.readerIndex()) {
                            buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                            throw new IOException("Decode without read data.");
                        }
                        if (msg != null) {
                            //msg为MultiMessage(保存了Request/Response集合) 或者Request/Response，解码完成后交给netty channel继续处理
                            //3
                            Channels.fireMessageReceived(ctx, msg, event.getRemoteAddress());
                        }
                    }
                    //判断是否可读 ，如果可读 继续解码 什么时候message.readable() 会返回true： message是由多个完整包+一个不完整包组成 
                } while (message.readable());//4
            } finally {
            
        
                //这种情况主要是多个完整包+1个不完整包 或者1个不完整包
                if (message.readable()) {
                    //5
                    message.discardReadBytes(); //将已经读取的抛弃，还未读取的移到从0开始，读指针设为0
                    buffer = message;
                } else {
                    //现有buffer已经解码完成 新创建一个buffer 读写指针都为0
                    //6
                    buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                }
                NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            ctx.sendUpstream(e);
        }
    }

```
上面解码顺序
- 情况1：多个或者单个完整包 1->3->4->6   msg=MultiMessage (多个)、Request/Response(单个)
- 情况2：多个或者单个完整包+一个不完整包 1->3->4->1->2-5   msg=MultiMessage (多个)、Request/Response(单个)和 Codec2.DecodeResult.NEED_MORE_INPUT
- 情况3：一个不完整包 1->2->5   msg= Codec2.DecodeResult.NEED_MORE_INPUT


```java
public final class DubboCountCodec implements Codec2{
  public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        //读指针 
        int save = buffer.readerIndex();
        MultiMessage result = MultiMessage.create();
        //拆包逻辑
        //可能多个包粘连一起 对多个包循环解码  ，然后放到MultiMessage result
        do {
            //codec =DubboCodec
            /**
            *->ExchangeCodec
            *-->DubboCodec
            */
            Object obj = codec.decode(channel, buffer);
            //不是完整包 读指针还原到调用codec.decode(channel, buffer);的位置
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {
                //正常解码 返回的obj=Request  Request的mData域存储了RpcInvocation(此时为DecodeableRpcInvocation)
                result.addMessage(obj);
                //RpcInvocation设置此次读取的长度到attachments->key:input val:长度
                logMessageLength(obj, buffer.readerIndex() - save);
                //更新读指针
                save = buffer.readerIndex();
            }
        } while (true);
        //此次buffer 没有解码到一个完整的Request,包不完整
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }
        if (result.size() == 1) {
            return result.get(0);
        }
        return result;
    }
}
```

```java
public class ExchangeCodec extends TelnetCodec {
      public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
      //可读字节readable= this.writerIndex - this.readerIndex
        int readable = buffer.readableBytes();
        //开辟当前头数组大小Math.min(readable, HEADER_LENGTH)
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        return decode(channel, buffer, readable, header);
    }
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        // check magic number.
        if (readable > 0 && header[0] != MAGIC_HIGH
                || readable > 1 && header[1] != MAGIC_LOW) {
            int length = header.length;
            if (header.length < readable) {
                header = Bytes.copyOf(header, readable);
                buffer.readBytes(header, length, readable - length);
            }
            for (int i = 1; i < header.length - 1; i++) {
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    buffer.readerIndex(buffer.readerIndex() - header.length + i);
                    header = Bytes.copyOf(header, i);
                    break;
                }
            }
            //TelnetCodec
            return super.decode(channel, buffer, readable, header);
        }
        // check length. 可读字节小于包头长 不是完整包 直接返回 需要继续拼接下一个包
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // get data length. 包长度
        int len = Bytes.bytes2int(header, 12);
        //检验包大小 默认8m
        checkPayload(channel, len);

        int tt = len + HEADER_LENGTH;
        //包长不足继续下一个包
        if (readable < tt) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // limit input stream.
        ChannelBufferInputStream is = new ChannelBufferInputStream(buffer, len);

        try {
           //->DubboCodec.decodeBody
            return decodeBody(channel, is, header);
        } finally {
            //buffer是否已经读完 
            if (is.available() > 0) {
                try {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip input stream " + is.available());
                    }
                    //未读完直接跳到末尾
                    StreamUtils.skipUnusedStream(is);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
    }
}
```

```java
public class DubboCodec extends ExchangeCodec implements Codec2 {
 protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        Serialization s = CodecSupport.getSerialization(channel.getUrl(), proto);
        // get request id.
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) {
            // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);
            }
            // get status.
            byte status = header[3];
            res.setStatus(status);
            if (status == Response.OK) {
                try {
                    Object data;
                    //心跳
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                    
                    //事件    
                    } else if (res.isEvent()) {
                        data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                    } else {
                        //Request
                        DecodeableRpcResult result;
                        if (channel.getUrl().getParameter(
                                Constants.DECODE_IN_IO_THREAD_KEY,
                                Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else {
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    if (log.isWarnEnabled()) {
                        log.warn("Decode response failed: " + t.getMessage(), t);
                    }
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {
                res.setErrorMessage(deserialize(s, channel.getUrl(), is).readUTF());
            }
            return res;
        } else {
            // decode request. 复原发送的Request
            Request req = new Request(id);
            req.setVersion("2.0.0");
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                if (req.isHeartbeat()) {
                    data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                } else if (req.isEvent()) {
                    data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                } else {
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(
                            Constants.DECODE_IN_IO_THREAD_KEY,
                            Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        //真正解码 DecodeableRpcInvocation
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }
}
```

```java
public class DecodeableRpcInvocation extends RpcInvocation implements Codec, Decodeable {
      public void decode() throws Exception {
        if (!hasDecoded && channel != null && inputStream != null) {
            try {
                decode(channel, inputStream);
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc invocation failed: " + e.getMessage(), e);
                }
                request.setBroken(true);
                request.setData(e);
            } finally {
                hasDecoded = true;
            }
        }
    }
     public Object decode(Channel channel, InputStream input) throws IOException {
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
                .deserialize(channel.getUrl(), input);

        setAttachment(Constants.DUBBO_VERSION_KEY, in.readUTF());
        setAttachment(Constants.PATH_KEY, in.readUTF());
        setAttachment(Constants.VERSION_KEY, in.readUTF());

        setMethodName(in.readUTF());
        try {
            Object[] args;
            Class<?>[] pts;
            String desc = in.readUTF();
            if (desc.length() == 0) {
                pts = DubboCodec.EMPTY_CLASS_ARRAY;
                args = DubboCodec.EMPTY_OBJECT_ARRAY;
            } else {
                pts = ReflectUtils.desc2classArray(desc);
                args = new Object[pts.length];
                for (int i = 0; i < args.length; i++) {
                    try {
                        args[i] = in.readObject(pts[i]);
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Decode argument failed: " + e.getMessage(), e);
                        }
                    }
                }
            }
            setParameterTypes(pts);

            Map<String, String> map = (Map<String, String>) in.readObject(Map.class);
            if (map != null && map.size() > 0) {
                Map<String, String> attachment = getAttachments();
                if (attachment == null) {
                    attachment = new HashMap<String, String>();
                }
                attachment.putAll(map);
                setAttachments(attachment);
            }
            //decode argument ,may be callback
            for (int i = 0; i < args.length; i++) {
                args[i] = decodeInvocationArgument(channel, this, pts, i, args[i]);
            }

            setArguments(args);

        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read invocation data failed.", e));
        }
        return this;
    }

}
```
### 服务端解码完成后
NettyHandler->
@Sharable
public class NettyHandler extends SimpleChannelHandler {
```java
 @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);
        try {
            //handler=NettyServer  e.getMessage()=MultiMessage(保存了Request/Response集合) 或者Request/Response
            handler.received(channel, e.getMessage());
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
        }
    }
}    
```

```java
public abstract class AbstractPeer implements Endpoint, ChannelHandler {
      public void received(Channel ch, Object msg) throws RemotingException {
        if (closed) {
            return;
        }
        //handler=MultiMessageHandler
        handler.received(ch, msg);
    }
}
```

```java
public class MultiMessageHandler extends AbstractChannelHandlerDelegate {

    public MultiMessageHandler(ChannelHandler handler) {
        super(handler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        //message为MultiMessage(一次性解码有多个包完整包拆包后的结果) 遍历继续处理
        if (message instanceof MultiMessage) {
            MultiMessage list = (MultiMessage) message;
            for (Object obj : list) {
                //HeartbeatHandler
                handler.received(channel, obj);
            }
        } else {
             //HeartbeatHandler
            handler.received(channel, message);
        }
    }
}
```
```java
public class HeartbeatHandler extends AbstractChannelHandlerDelegate {
 public void received(Channel channel, Object message) throws RemotingException {
        setReadTimestamp(channel);
        //心跳 请求 回应请求
        if (isHeartbeatRequest(message)) {
            Request req = (Request) message;
            if (req.isTwoWay()) {
                Response res = new Response(req.getId(), req.getVersion());
                res.setEvent(Response.HEARTBEAT_EVENT);
                channel.send(res);
                if (logger.isInfoEnabled()) {
                    int heartbeat = channel.getUrl().getParameter(Constants.HEARTBEAT_KEY, 0);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Received heartbeat from remote channel " + channel.getRemoteAddress()
                                + ", cause: The channel has no data-transmission exceeds a heartbeat period"
                                + (heartbeat > 0 ? ": " + heartbeat + "ms" : ""));
                    }
                }
            }
            return;
        }
        //回复心跳 不做处理
        if (isHeartbeatResponse(message)) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        new StringBuilder(32)
                                .append("Receive heartbeat response in thread ")
                                .append(Thread.currentThread().getName())
                                .toString());
            }
            return;
        }
        //不是心跳继续下一个处理器 AllChannelHandler
        handler.received(channel, message);
    }
}    
```

```java
public class AllChannelHandler extends WrappedChannelHandler {
 public void received(Channel channel, Object message) throws RemotingException {
        ExecutorService cexecutor = getExecutorService();
        try {
            //将请求放入线程池处理
            cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
        } catch (Throwable t) {
            //TODO 临时解决线程池满后异常信息无法发送到对端的问题。待重构
            //fix 线程池满了拒绝调用不返回，导致消费者一直等待超时
        	if(message instanceof Request && t instanceof RejectedExecutionException){
        		Request request = (Request)message;
        		if(request.isTwoWay()){
        			String msg = "Server side(" + url.getIp() + "," + url.getPort() + ") threadpool is exhausted ,detail msg:" + t.getMessage();
        			Response response = new Response(request.getId(), request.getVersion());
        			response.setStatus(Response.SERVER_THREADPOOL_EXHAUSTED_ERROR);
        			response.setErrorMessage(msg);
        			channel.send(response);
        			return;
        		}
        	}
            throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
        }
    }
}
```


- 下面主要是多种请求事件进行处理,dubbo用它对多种不同通讯协议归一处理
```java
public class ChannelEventRunnable implements Runnable {
    public void run() {
            switch (state) {
                case CONNECTED:
                    try {
                        handler.connected(channel);
                    } catch (Exception e) {
                        logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                    }
                    break;
                case DISCONNECTED:
                    try {
                        handler.disconnected(channel);
                    } catch (Exception e) {
                        logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                    }
                    break;
                case SENT:
                    try {
                        handler.sent(channel, message);
                    } catch (Exception e) {
                        logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                                + ", message is " + message, e);
                    }
                    break;
                //收到普通请求处理    
                case RECEIVED:
                    try {
                        //handler=DecodeHandler
                        handler.received(channel, message);
                    } catch (Exception e) {
                        logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                                + ", message is " + message, e);
                    }
                    break;
                case CAUGHT:
                    try {
                        handler.caught(channel, exception);
                    } catch (Exception e) {
                        logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                                + ", message is: " + message + ", exception is " + exception, e);
                    }
                    break;
                default:
                    logger.warn("unknown state: " + state + ", message is " + message);
            }
        }
}
```

```java
public class DecodeHandler extends AbstractChannelHandlerDelegate {
      public void received(Channel channel, Object message) throws RemotingException {
        if (message instanceof Decodeable) {
            decode(message);
        }

        if (message instanceof Request) {
            //进行一次判断是否已经解码 未解码的情况进行解码 感觉冗余了 在DubboCodec已经解码过了
            //((Request) message).getData()=RpcInvocation
            decode(((Request) message).getData());
        }

        if (message instanceof Response) {
            decode(((Response) message).getResult());
        }
        //HeaderExchangeHandler
        handler.received(channel, message);
    }
}
```

```java
public class HeaderExchangeHandler implements ChannelHandlerDelegate {
  public void received(Channel channel, Object message) throws RemotingException {
        channel.setAttribute(KEY_READ_TIMESTAMP, System.currentTimeMillis());
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            if (message instanceof Request) {
                // handle request.
                Request request = (Request) message;
                if (request.isEvent()) {
                    handlerEvent(channel, request);
                } else {
                    //判断是否双向 需要回复消息  handler=DubboProtocol.requestHandler
                    if (request.isTwoWay()) {
                        Response response = handleRequest(exchangeChannel, request);
                        channel.send(response);
                    } else {
                        handler.received(exchangeChannel, request.getData());
                    }
                }
            } else if (message instanceof Response) {
                handleResponse(channel, (Response) message);
            } else if (message instanceof String) {
                if (isClientSide(channel)) {
                    Exception e = new Exception("Dubbo client can not supported string message: " + message + " in channel: " + channel + ", url: " + channel.getUrl());
                    logger.error(e.getMessage(), e);
                } else {
                    String echo = handler.telnet(channel, (String) message);
                    if (echo != null && echo.length() > 0) {
                        channel.send(echo);
                    }
                }
            } else {
                handler.received(exchangeChannel, message);
            }
        } finally {
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
    }
    Response handleRequest(ExchangeChannel channel, Request req) throws RemotingException {
        Response res = new Response(req.getId(), req.getVersion());
        if (req.isBroken()) {
            Object data = req.getData();

            String msg;
            if (data == null) msg = null;
            else if (data instanceof Throwable) msg = StringUtils.toString((Throwable) data);
            else msg = data.toString();
            res.setErrorMessage("Fail to decode request due to: " + msg);
            res.setStatus(Response.BAD_REQUEST);

            return res;
        }
        // find handler by message class.
        Object msg = req.getData();
        try {
            //发布的ref bean调用
            // handle data.  DubboProtocol.requestHandler.reply 
            Object result = handler.reply(channel, msg);
            res.setStatus(Response.OK);
            res.setResult(result);
        } catch (Throwable e) {
            res.setStatus(Response.SERVICE_ERROR);
            res.setErrorMessage(StringUtils.toString(e));
        }
        return res;
    }

}
```

```java
public class DubboProtocol extends AbstractProtocol {
    //在AbstractProtocol里面 protected 子类可以访问
    protected final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<String, Exporter<?>>();
    
     private ExchangeHandler requestHandler = new ExchangeHandlerAdapter() {

        public Object reply(ExchangeChannel channel, Object message) throws RemotingException {
            if (message instanceof Invocation) {
                Invocation inv = (Invocation) message;
                //获取ref bean代理后的Invoker 从缓存exporterMap中获取 发布的所有接口到保存在exporterMap中
                Invoker<?> invoker = getInvoker(channel, inv);
                //如果是callback 需要处理高版本调用低版本的问题
                if (Boolean.TRUE.toString().equals(inv.getAttachments().get(IS_CALLBACK_SERVICE_INVOKE))) {
                    String methodsStr = invoker.getUrl().getParameters().get("methods");
                    boolean hasMethod = false;
                    if (methodsStr == null || methodsStr.indexOf(",") == -1) {
                        hasMethod = inv.getMethodName().equals(methodsStr);
                    } else {
                        String[] methods = methodsStr.split(",");
                        for (String method : methods) {
                            if (inv.getMethodName().equals(method)) {
                                hasMethod = true;
                                break;
                            }
                        }
                    }
                    if (!hasMethod) {
                        logger.warn(new IllegalStateException("The methodName " + inv.getMethodName() + " not found in callback service interface ,invoke will be ignored. please update the api interface. url is:" + invoker.getUrl()) + " ,invocation is :" + inv);
                        return null;
                    }
                }
                RpcContext.getContext().setRemoteAddress(channel.getRemoteAddress());
                return invoker.invoke(inv);
            }
            throw new RemotingException(channel, "Unsupported request: " + message == null ? null : (message.getClass().getName() + ": " + message) + ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress());
        }

        @Override
        public void received(Channel channel, Object message) throws RemotingException {
            if (message instanceof Invocation) {
                reply((ExchangeChannel) channel, message);
            } else {
                super.received(channel, message);
            }
        }

        @Override
        public void connected(Channel channel) throws RemotingException {
            invoke(channel, Constants.ON_CONNECT_KEY);
        }

        @Override
        public void disconnected(Channel channel) throws RemotingException {
            if (logger.isInfoEnabled()) {
                logger.info("disconected from " + channel.getRemoteAddress() + ",url:" + channel.getUrl());
            }
            invoke(channel, Constants.ON_DISCONNECT_KEY);
        }

        private void invoke(Channel channel, String methodKey) {
            Invocation invocation = createInvocation(channel, channel.getUrl(), methodKey);
            if (invocation != null) {
                try {
                    received(channel, invocation);
                } catch (Throwable t) {
                    logger.warn("Failed to invoke event method " + invocation.getMethodName() + "(), cause: " + t.getMessage(), t);
                }
            }
        }

        private Invocation createInvocation(Channel channel, URL url, String methodKey) {
            String method = url.getParameter(methodKey);
            if (method == null || method.length() == 0) {
                return null;
            }
            RpcInvocation invocation = new RpcInvocation(method, new Class<?>[0], new Object[0]);
            invocation.setAttachment(Constants.PATH_KEY, url.getPath());
            invocation.setAttachment(Constants.GROUP_KEY, url.getParameter(Constants.GROUP_KEY));
            invocation.setAttachment(Constants.INTERFACE_KEY, url.getParameter(Constants.INTERFACE_KEY));
            invocation.setAttachment(Constants.VERSION_KEY, url.getParameter(Constants.VERSION_KEY));
            if (url.getParameter(Constants.STUB_EVENT_KEY, false)) {
                invocation.setAttachment(Constants.STUB_EVENT_KEY, Boolean.TRUE.toString());
            }
            return invocation;
        }
    };
 Invoker<?> getInvoker(Channel channel, Invocation inv) throws RemotingException {
        boolean isCallBackServiceInvoke = false;
        boolean isStubServiceInvoke = false;
        int port = channel.getLocalAddress().getPort();
        String path = inv.getAttachments().get(Constants.PATH_KEY);
        //如果是客户端的回调服务.
        isStubServiceInvoke = Boolean.TRUE.toString().equals(inv.getAttachments().get(Constants.STUB_EVENT_KEY));
        if (isStubServiceInvoke) {
            port = channel.getRemoteAddress().getPort();
        }
        //callback
        isCallBackServiceInvoke = isClientSide(channel) && !isStubServiceInvoke;
        if (isCallBackServiceInvoke) {
            path = inv.getAttachments().get(Constants.PATH_KEY) + "." + inv.getAttachments().get(Constants.CALLBACK_SERVICE_KEY);
            inv.getAttachments().put(IS_CALLBACK_SERVICE_INVOKE, Boolean.TRUE.toString());
        }
        //serviceKey=com.alibaba.dubbo.demo.DemoService:20883
        String serviceKey = serviceKey(port, path, inv.getAttachments().get(Constants.VERSION_KEY), inv.getAttachments().get(Constants.GROUP_KEY));
        //获取提供者发布缓存的Export
        DubboExporter<?> exporter = (DubboExporter<?>) exporterMap.get(serviceKey);

        if (exporter == null)
            throw new RemotingException(channel, "Not found exported service: " + serviceKey + " in " + exporterMap.keySet() + ", may be version or group mismatch " + ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress() + ", message:" + inv);

        return exporter.getInvoker();
    }
}
```
获取的invoker:
![image](/images/clipboard2.png)
![image](/images/clipboard3.png)

先到ProtocolFilterWrapper创建的拦截器Filter链，最终到达Wrapper动态生产的代理类,真正开始对发布的接口进行调用。

## 提供者编码
返回Response 待续...