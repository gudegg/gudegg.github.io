---
title: netty框架精髓
date: 2016-02-11 20:33:28
tags: netty
---

#####  ChannelPipeline, Channel, ChannelHandler和ChannelHandlerContext的关系:

![image-20200807145609215](https://gitee.com/zhangguodong/image/raw/master/picgo/image-20200807145609215.png)
<!--more-->

1. Channel 绑定到 ChannelPipeline
2.   ChannelPipeline 绑定到 包含 ChannelHandler 的 Channel
3.   ChannelHandler
5.   当添加 ChannelHandler 到 ChannelPipeline 时，ChannelHandlerContext 被创建

```java
ChannelHandlerContext ctx = context;
Channel channel = ctx.channel();  //1
channel.write(Unpooled.copiedBuffer("Netty in Action",CharsetUtil.UTF_8));  //2

```

1. 得到与 ChannelHandlerContext 关联的 Channel 的引用
2.  通过 Channel 写缓存

##### 从ChannelHandlerContext获取到 ChannelPipeline的相同示例

```java
  ChannelHandlerContext ctx = context;
  ChannelPipeline pipeline = ctx.pipeline(); //1
  pipeline.write(Unpooled.copiedBuffer("Netty in Action", CharsetUtil.UTF_8));  //2
```


1. 得到与 ChannelHandlerContext 关联的 ChannelPipeline 的引用
1.  通过 ChannelPipeline 写缓冲区

选自[Netty 实战(精髓)](https://waylau.gitbooks.io/essential-netty-in-action/content/CORE%20FUNCTIONS/ChannelHandlerContext.html)