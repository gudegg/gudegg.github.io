---
title: java4种引用
date: 2017-12-09 15:47:06
tags: java虚拟机
---

今天在看HikariCP源码ConcurrentBag类时，发现它里面使用了WeakReference，第一次看到这个，之前从来没用过，于是查资料去了解下。

### 引用类型
Java中一共有4中引用，分别为强引用(Strong Reference)、软引用(Soft Reference、弱引用(Weak Reference)、虚引用(Phantom Reference)，强度依次递减。

#### 强引用
java的默认实现，类似`Object obj=new Object()`,它会尽可能长时间存在于JVM，当没有任何对象指向它的时候才会被GC回收
```java
    @Test
    public void strongRefTest(){
        Object obj=new Object();
        Object strongRef=obj;
        obj=null;
        System.gc();
        /***
         * GC后不会被回收
         */
        assertNotNull(strongRef);
    }
```
<!--more-->    

#### 软引用
 当所引用的对象在 JVM 内不再有强引用时，它会尽可能保留引用直到JVM内存不足时才会被回收，如果此次回收还没有足够的内存，就会抛出内存溢出异常。在JDK1.2后提供SoftReference类来实现软引用，这一特性使得它非常适合缓存应用。
```java
    //-XX:+PrintGCDetails -Xmx10m 
    @Test
    public void SoftRefTest() {
        Map map = new HashMap();
        byte[] a = new byte[1024 * 1024 * 5];
        SoftReference<byte[]> softReference = new SoftReference<byte[]>(a);
       
        a=null;
        map.put("a", softReference);
        assertNotNull(((SoftReference)(map.get("a"))).get());
        //在这里发生Full GC,对象a、softReference都被回收
        byte[] c = new byte[1024 * 1024 * 5];
        assertNull(((SoftReference)(map.get("a"))).get());
    }
```
> a=null并不是需要显示调用，由于这里创建的a是大对象，直接进入老年代，一个对象要想被回收，至少要被可达性分析标记2次，因此这里要显示置为null，才能在Full GC时被回收。

测试GC日志:
```xml
[GC (Allocation Failure) [PSYoungGen: 2048K->504K(2560K)] 2048K->832K(9728K), 0.0009832 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2552K->488K(2560K)] 2880K->1223K(9728K), 0.0007856 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] [GC (Allocation Failure) [PSYoungGen: 2536K->480K(2560K)] 3271K->1607K(9728K), 0.0008566 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] [GC (Allocation Failure) [PSYoungGen: 2279K->504K(2560K)] 8526K->6979K(9728K), 0.0009122 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[Full GC (Ergonomics) [PSYoungGen: 504K->0K(2560K)] [ParOldGen: 6475K->6542K(7168K)] 6979K->6542K(9728K), [Metaspace: 4693K->4693K(1056768K)], 0.0129634 secs] [Times: user=0.11 sys=0.00, real=0.01 secs] 
[GC (Allocation Failure) [PSYoungGen: 0K->0K(2560K)] 6542K->6542K(9728K), 0.0002775 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[Full GC (Allocation Failure) [PSYoungGen: 0K->0K(2560K)][ParOldGen: 6542K->1377K(5632K)] 6542K->1377K(8192K), [Metaspace: 4693K->4693K(1056768K)], 0.0093335 secs] [Times: user=0.00 sys=0.00, real=0.01 secs] 
Heap
 PSYoungGen      total 2560K, used 85K [0x00000000ffd00000, 0x0000000100000000, 0x0000000100000000)
  eden space 2048K, 4% used [0x00000000ffd00000,0x00000000ffd15440,0x00000000fff00000)
  from space 512K, 0% used [0x00000000fff00000,0x00000000fff00000,0x00000000fff80000)
  to   space 512K, 0% used [0x00000000fff80000,0x00000000fff80000,0x0000000100000000)
 ParOldGen       total 7168K, used 6497K [0x00000000ff600000, 0x00000000ffd00000, 0x00000000ffd00000)
  object space 7168K, 90% used [0x00000000ff600000,0x00000000ffc58740,0x00000000ffd00000)
 Metaspace       used 4713K, capacity 5066K, committed 5248K, reserved 1056768K
  class space    used 548K, capacity 562K, committed 640K, reserved 1048576K
```
在执行`byte[] c = new byte[1024 * 1024 * 5];`时会发生GC，[Full GC (Allocation Failure) [PSYoungGen: 0K->0K(2560K)]**[ParOldGen: 6542K->1377K(5632K)] 6542K->1377K(8192K)**, [Metaspace: 4693K->4693K(1056768K)], 0.0093335 secs] [Times: user=0.00 sys=0.00, real=0.01 secs] ，softReference 被回收。

#### 弱引用
 当所引用的对象在 JVM 内不再有强引用时, GC 后 weakReference 将会被自动回收。在JDK1.2后提供weakReference类来实现弱引用
 ```java
     @Test
    public void weakRefTest() throws InterruptedException {
        byte[] a = new byte[1024 * 1024 * 5];
        WeakReference<byte[]> weakReference = new WeakReference<byte[]>(a);
        a=null;
        assertNotNull(weakReference.get());
        System.gc();
        assertNull(weakReference.get());
    }
 ```
 > WeakHashMap 使用 WeakReference 作为 key， 一旦没有指向 key 的强引用, WeakHashMap 在 GC 后将自动删除相关的 entry
 ```java
    @Test
    public void testWeakHashMap() throws InterruptedException {
        Map<Object, Object> weakHashMap = new WeakHashMap<Object, Object>();
        Object key = new Object();
        Object value = new Object();
        weakHashMap.put(key, value);

        assertTrue(weakHashMap.containsValue(value));

        key = null;
        System.gc();

        /**
         * 等待无效 entries 进入 ReferenceQueue 以便下一次调用 getTable 时被清理 休眠让GC清理掉entry System.gc()不能100%触发GC
         */
        Thread.sleep(100);

        /**
         * 一旦没有指向 key 的强引用, WeakHashMap 在 GC 后将自动删除相关的 entry
         */
        assertFalse(weakHashMap.containsValue(value));
    }
 ```
#### 虚引用
 也称幽灵引用或者幻影引用，一个对象是否有虚引用的存在，完全不会影响其生存时间构成影响。为对象设置虚引用关联的唯一目的就是能在这个对象被收集器回收时收到一个系统通知。在JDK1.2后提供PhantomReference类来实现弱引用。

```java
    @Test
    public void phantomRefTest() {
        Object referent = new Object();
        PhantomReference<Object> phantomReference = new PhantomReference<Object>(referent, new ReferenceQueue<Object>());

        /**
         * phantom reference 的 get 方法永远返回 null
         */
        assertNull(phantomReference.get());
    }
```
一个永远返回 null 的 reference 要来何用,  请注意构造 PhantomReference 时的第二个参数 ReferenceQueue(事实上 WeakReference & SoftReference 也可以有这个参数)，PhantomReference 唯一的用处就是跟踪 referent  何时被 enqueue 到 ReferenceQueue 中.
###  RererenceQueue
> 当一个 WeakReference 开始返回 null 时， 它所指向的对象已经准备被回收， 这时可以做一些合适的清理工作.   将一个 ReferenceQueue 传给一个 Reference 的构造函数， 当对象被回收时， 虚拟机会自动将这个对象插入到 ReferenceQueue 中， WeakHashMap 就是利用 ReferenceQueue 来清除 key 已经没有强引用的 entries.
```java
    @Test
    public void referenceQueueTest() throws InterruptedException {
        Object referent = new Object();
        ReferenceQueue<Object> referenceQueue = new ReferenceQueue<Object>();
        WeakReference<Object> weakReference = new WeakReference<Object>(referent, referenceQueue);
        //是否已经加入referenceQueue 没有GC故为false
        assertFalse(weakReference.isEnqueued());
        Reference<? extends Object> polled = referenceQueue.poll();
        assertNull(polled);

        referent = null;
        System.gc();
        
        Thread.sleep(100);
        assertTrue(weakReference.isEnqueued());
        Reference<? extends Object> removed = referenceQueue.remove();
        assertNotNull(removed);
    }
```

参考[理解Java的GC与幽灵引用](http://www.iteye.com/topic/401478)、[不只是给面试加分--Java WeakReference的理](http://puretech.iteye.com/blog/2008663)