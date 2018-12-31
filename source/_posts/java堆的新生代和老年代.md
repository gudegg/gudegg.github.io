---
title: java堆-新生代和老年代
date: 2017-12-02 23:38:21
tags: java虚拟机
---

java堆是垃圾收集器的主要管理区域，堆可以细分为：**新生代和老年代**,再细致一点可以分为:**Eden区、from survivor区和to survivor区**。
#### 堆内存
Java中的堆是JVM所管理的最大的一块内存空间，主要用于存放各种类的实例对象。
**老年代:新生代**=>默认情况下是2:1,可以通过`XX:NewRatio=ratio`调整比例，默认为2,详细可看[官方文档](https://docs.oracle.com/cd/E19900-01/819-4742/abeik/index.html)
> By default, the Application Server is invoked with the Java HotSpot Server JVM. The default NewRatio for the Server JVM is 2: the old generation occupies 2/3 of the heap while the new generation occupies 1/3. The larger new generation can accommodate many more short-lived objects, decreasing the need for slow major collections. The old generation is still sufficiently large enough to hold many long-lived objects

**Eden区:from Survivor区:to Survivor区**=>默认情况下是8:1:1，可以通过 `-XX:SurvivorRatio=size` 来调整比例，默认是8

<!--more-->
#### GC堆
GC分为两种:Minor GC、Full GC(或称为Major GC)。[详解GC参考](/2017/11/28/%E5%86%85%E5%AD%98%E5%88%86%E9%85%8D%E5%92%8C%E5%9B%9E%E6%94%B6%E7%AD%96%E7%95%A5/#空间分配担保)

#### 虚拟机常用参数
| 参数 | 描述 |
| :---: |:---:|
|-Xms|	初始堆大小。如：-Xms256m|
|-Xmx	|最大堆大小。如：-Xmx512m|
|-Xmn|	新生代大小。通常为 Xmx 的 1/3 或 1/4。新生代 = Eden + 2 个 Survivor 空间。实际可用空间为 = Eden + 1 个 Survivor，即 90% |
|-Xss	|JDK1.5+ 每个线程堆栈大小为 1M，一般来说如果栈不是很深的话， 1M 是绝对够用了的。|
|-XX:NewRatio|	新生代与老年代的比例，如 –XX:NewRatio=2，则新生代占整个堆空间的1/3，老年代占2/3|
|-XX:SurvivorRatio	|新生代中 Eden 与 Survivor 的比值。默认值为 8。即 Eden 占新生代空间的 8/10，另外两个 Survivor 各占 1/10 |
|-XX:PermSize	|永久代(方法区)的初始大小 (jdk1.8中,永久代最终被移除,方法区移至Metaspace)|
|-XX:MaxPermSize	|永久代(方法区)的最大值 (jdk1.8中,永久代最终被移除,方法区移至Metaspace)|
|-XX:MetaspaceSize|设置元空间大小,如:-XX:MetaspaceSize=128M|
|-XX:MaxMetaspaceSize|设置元空间最大大小|
|-XX:+PrintGCDetails	|打印 GC 信息|
|-XX:+HeapDumpOnOutOfMemoryError|	让虚拟机在发生内存溢出时 Dump 出当前的内存堆转储快照，以便分析用|