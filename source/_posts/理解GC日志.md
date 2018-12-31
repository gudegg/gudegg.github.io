---
title: 理解GC日志
date: 2017-11-25 19:25:22
tags: java虚拟机
---

> GC日志是一个很重要的工具，它准确记录了每一次的GC的执行时间和执行结果，通过分析GC日志可以优化堆设置和GC设置，或者改进应用程序的对象分配模式。

#### -XX:+PrintGC

参数-XX:+PrintGC（或者-verbose:gc）开启了**简单GC日志模式**，为每一次新生代（young generation）的GC和每一次的Full GC打印一行信息。下面举例说明：

```
[GC 246656K->243120K(376320K), 0.0929090 secs]
[Full GC 243120K->241951K(629760K), 1.5589690 secs]
```
每行开始首先是GC的类型（可以是“GC”或者“Full GC”），然后是在GC之前和GC之后已使用的堆空间，再然后是当前的堆容量，最后是GC持续的时间（以秒计）。

#### -XX:+PrintGCDetails
开启了**详细GC日志模式**。在这种模式下，日志格式和所使用的GC算法有关。
```
[GC (Metadata GC Threshold) [PSYoungGen: 19427K->4592K(95232K)] 29052K->15061K(129536K), 0.0051148 secs] [Times: user=0.01 sys=0.00, real=0.01 secs] 
[Full GC (Metadata GC Threshold) [PSYoungGen: 4592K->0K(95232K)] [ParOldGen: 10469K->9121K(34304K)] 15061K->9121K(129536K), [Metaspace: 20783K->20783K(1067008K)], 0.0407688 secs] [Times: user=0.13 sys=0.00, real=0.04 secs] 
[GC (Allocation Failure)) [PSYoungGen: 35510K->3651K(92160K)] 44632K->12780K(126464K), 0.0040306 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[Full GC (System.gc()) [PSYoungGen: 3651K->0K(92160K)] [ParOldGen: 9129K->9619K(34304K)] 12780K->9619K(126464K), [Metaspace: 22762K->22762K(1069056K)], 0.0613941 secs] [Times: user=0.27 sys=0.00, real=0.06 secs] 
```

#### 新生代GC
```
[GC (Allocation Failure) [PSYoungGen: 35510K->3651K(92160K)] 44632K->12780K(126464K), 0.0040306 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
```
- `[GC`与`[Full GC`表示GC类型
- `[PSYoungGen: 35510K->3651K(92160K)]` ，其中[PSYoungGen表示GC发生的区域，显示的区域与使用的GC收集器密切相关。这里表示使用了Parallel Scavenge收集器，如果使用Serial收集器，则显示`[DefNew`,ParNew收集器则为[ParNew。`35510K->3651K(92160K)`对应：收集前**该内存区域（新生代）**已使用容量->GC后**该内存区域**已使用容量(**该内存区域**总容量)。`44632K->12780K(126464K)`表示GC前**java堆**已使用容量->GC后**java堆**已使用容量(**java堆总容量**)
- 0.0040306 secs sec表示该内存区域GC所用时间
- `[Times: user=0.00 sys=0.00, real=0.00 secs] `分别代表用户态消耗的CPU时间、内核态消耗的CPU时间和操作从开始到结束所经过的墙钟时间。**墙钟时间**与**CPU时间**的区别是墙钟时间包括各种非计算的等待耗时，如磁盘I/O、线程阻塞等待。
<!--more-->
---
#### 老年代GC

```
[Full GC (System.gc()) [PSYoungGen: 3651K->0K(92160K)] [ParOldGen: 9129K->9619K(34304K)] 12780K->9619K(126464K), [Metaspace: 22762K->22762K(1069056K)], 0.0613941 secs] [Times: user=0.27 sys=0.00, real=0.06 secs] 
```
- [ParOldGen: 9129K->9619K(34304K)]表示使用了Parallel Old收集器，` 9129K->9619K(34304K)`表示收集前**该内存区域（老年代）**已使用容量->GC后**该内存区域**已使用容量(**该内存区域**总容量)
- [Metaspace: 22762K->22762K(1069056K)]表示**元空间(Metaspace)**GC前已使用容量->GC后已使用容量(该区域总容量)
- 其他与新生代GC相似
---

> 我在spring boot程序每次启动时，都会打印出出如下2句GC日志,主要是由于元空间不足导致的。在JDK8中,元空间大小是没有上限的,最大容量与机器的内存有关;在64位机器中，默认大小为21M。当我设置了`-XX:MetaspaceSize=128M`后，就不会打印这2个GC日志。

```
[GC (Metadata GC Threshold) [PSYoungGen: 19427K->4592K(95232K)] 29052K->15061K(129536K), 0.0051148 secs] [Times: user=0.01 sys=0.00, real=0.01 secs] 
[Full GC (Metadata GC Threshold) [PSYoungGen: 4592K->0K(95232K)] [ParOldGen: 10469K->9121K(34304K)] 15061K->9121K(129536K), [Metaspace: 20783K->20783K(1067008K)], 0.0407688 secs] [Times: user=0.13 sys=0.00, real=0.04 secs] 
```
#### Metaspace常用VM参数
-XX:MaxMetaspaceSize=N 
这个参数用于限制Metaspace增长的上限，防止因为某些情况导致Metaspace无限的使用本地内存，影响到其他程序。

-XX:MinMetaspaceFreeRatio=N 
当进行过Metaspace GC之后，会计算当前Metaspace的空闲空间比，如果空闲比小于这个参数，那么虚拟机将增长Metaspace的大小。在本机该参数的默认值为40，也就是40%。设置该参数可以控制Metaspace的增长的速度，太小的值会导致Metaspace增长的缓慢，Metaspace的使用逐渐趋于饱和，可能会影响之后类的加载。而太大的值会导致Metaspace增长的过快，浪费内存。

-XX:MaxMetasaceFreeRatio=N 
当进行过Metaspace GC之后， 会计算当前Metaspace的空闲空间比，如果空闲比大于这个参数，那么虚拟机会释放Metaspace的部分空间。
-XX:MaxMetaspaceExpansion=N 
Metaspace增长时的最大幅度。

-XX:MinMetaspaceExpansion=N 
Metaspace增长时的最小幅度。

#### 元空间(Metaspace)：[永久代为什么被移出HotSpot JVM了](http://www.sczyh30.com/posts/Java/jvm-metaspace/)（针对jdk1.8）
> jdk1.8中,永久代最终被移除，**方法区移至Metaspace**，字符串常量移至Java Heap。永久代的垃圾回收主要两部分：废弃常量和无用类。


参考: [JVM实用参数（八）GC日志](http://ifeve.com/useful-jvm-flags-part-8-gc-logging/)、深入理解java虚拟机