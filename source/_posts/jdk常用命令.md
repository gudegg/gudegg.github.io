---
title: jdk常用命令
date: 2017-12-24 20:54:25
tags: java虚拟机
---

> 进程的本地虚拟机唯一ID(**LVMID**),LVMID与操作系统的进程ID(**PID**)是一致的。

#### jps
显示所有HotSpot虚拟机进程
1. `jps` 显示类名
1. `jps -l` 显示主类的全名
2. `jps -v` 输出进程启动的JVM参数

<!--more-->

#### jstat
收集HotSpot虚拟机各方面的运行数据
1. `jstat -gc vmid` 监视堆状况
> 如果是本地虚拟机进程**VMID**与**LVMID**相同，如果是远程虚拟机，VMID格式是：[protocol:][//]vmid[@hostname[:port]/servername] 

![jstat6](https://gitee.com/zhangguodong/image/raw/master/picgo/jstat6.png)
S0C：第一个幸存区的大小
S1C：第二个幸存区的大小
S0U：第一个幸存区的使用大小
S1U：第二个幸存区的使用大小
EC：Eden区的大小
EU：Eden区的使用大小
OC：老年代大小
OU：老年代使用大小
MC：方法区大小
MU：方法区使用大小
CCSC:压缩类空间大小
CCSU:压缩类空间使用大小
YGC：年轻代垃圾回收次数
YGCT：年轻代垃圾回收消耗时间
FGC：老年代垃圾回收次数
FGCT：老年代垃圾回收消耗时间
GCT：垃圾回收消耗总时间

2. `jstat -gcutil vmid` 与-gc基本相同，不过输出的是已使用空间占总空间的百分比
3. `jstat -gc -vmid 1000 20` 表示1000毫秒查询一次。共查询20次

#### jinfo
显示虚拟机配置信息
1 `jinfo vmid` 

#### jmap
生成虚拟机堆转换快照
1. `jmap -heap vmid` 显示虚拟机堆详细信息
2. `jmap -dump:live,format=b,file=heap.bin <pid>` live子参数说明是否只dump出存活的对象
3. `jmap -histo vmid` 显示堆中对象详细信息，包括类、实例数量、合计容量
示例：jmap -dump:format=b,file=test.bin 12444

#### jhat
虚拟机堆转存快照分析工具,配合jmap使用
1. `jhat test.bin`

#### jstack
堆跟踪工具
1. `jstack -l vmid` 除堆栈外，显示关于锁的附加信息

[oracle官方文档](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/jstat.html)

