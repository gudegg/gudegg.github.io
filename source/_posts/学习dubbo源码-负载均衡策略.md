---
title: 学习dubbo源码-负载均衡策略
date: 2019-01-13 21:25:00
tags: [dubbo]
---

Dubbo提供了**随机、轮询、最少活跃数、一致性哈希**4种负载均衡策略，默认为随机。

负载均衡策略抽象了`AbstractLoadBalance`,这4种策略都实现都继承它，实现doSelect方法。

```java
public abstract class AbstractLoadBalance implements LoadBalance {
    //根据预热时间与程序启动到现在的历时计算权重比例
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        //(uptime/warmup)*weight
        int ww = (int) ((float) uptime / ((float) warmup / (float) weight));
        return ww < 1 ? 1 : (ww > weight ? weight : ww);
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }
        //只有一个提供者直接返回
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        return doSelect(invokers, url, invocation);
    }

    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);

    /**
     * 权重随着预热时间不断增大，最后到达设置的权重
     * @param invoker
     * @param invocation
     * @return
     */
    protected int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT);
        if (weight > 0) {
            long timestamp = invoker.getUrl().getParameter(Constants.REMOTE_TIMESTAMP_KEY, 0L);
            if (timestamp > 0L) {
                int uptime = (int) (System.currentTimeMillis() - timestamp);
                //预热时间，默认10分钟
                int warmup = invoker.getUrl().getParameter(Constants.WARMUP_KEY, Constants.DEFAULT_WARMUP);
                if (uptime > 0 && uptime < warmup) {
                    weight = calculateWarmupWeight(uptime, warmup, weight);
                }
            }
        }
        return weight;
    }

}

```

有个很重要的就是权重会随着jvm运行时间不断增大，直到达到设置的权重。这样的好处是随着程序的运行，jvm会通过JIT(即使编译器)队热点代码做优化，提高代码执行效率。

<!--more-->

### Random LoadBalance
* **随机**，按权重设置随机概率。
* 在一个截面上碰撞的概率高，但调用量越大分布越均匀，而且按概率使用权重后也比较均匀，有利于动态调整提供者权重。

```java
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // Number of invokers
        //权重总合
        int totalWeight = 0;
        //所有提供者权重是否一致
        boolean sameWeight = true;
        for (int i = 0; i < length; i++) {
            //根据抽象类的预热代码获取权重 (1)
            int weight = getWeight(invokers.get(i), invocation);
            totalWeight += weight;
            //前一个提供者和后一个提供者对比权重是否一致 （2）
            if (sameWeight && i > 0
                    && weight != getWeight(invokers.get(i - 1), invocation)) {
                sameWeight = false;
            }
        }
        //权重和大于0并且权重都不相同
        if (totalWeight > 0 && !sameWeight) {
            // 根据权重和获取随机数
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < length; i++) {
                //相减直到偏移小于0，则选出此提供者昨晚本次调用 (3)
                offset -= getWeight(invokers.get(i), invocation);
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // 如果权重一致随机获取一个
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }

}
```

老代码(1)、(2)、(3)处重复获取了权重，可能导致获取权重值不一致，新版本做了如下优化.直接在第一次循环时保存每个提供者的权重。
```java
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Every invoker has the same weight?
        boolean sameWeight = true;
        // the weight of every invokers
        int[] weights = new int[length];
        // the first invoker's weight
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // The sum of weights
        int totalWeight = firstWeight;
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            // save for later use
            weights[i] = weight;
            // Sum
            totalWeight += weight;
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
```
### 
### RoundRobin LoadBalance
* **轮询**，按公约后的权重设置轮询比率。
* 存在慢的提供者累积请求的问题，比如：第二台机器很慢，但没挂，当请求调到第二台时就卡在那，久而久之，所有请求都卡在调到第二台上。

在此之前，Dubbo的轮询负载均衡算法**不那么平滑**，假设3个节点a、b、c的权重4、2、1，轮询的结果可能为abcabaa

目前最新版本轮询算法学习了Nginx的轮询负载思想：<br />假设3个节点abc，权重分别为4、2、1,则最终轮询选择的结果为abacaba<br />![image.png](https://cdn.nlark.com/yuque/0/2019/png/176230/1547902739056-df76af69-b022-45ed-9ff4-06edf9a52b9b.png#align=left&display=inline&height=275&linkTarget=_blank&name=image.png&originHeight=275&originWidth=567&size=14607&width=567)<br />主要思路就是选择权重最大的节点(即本次轮询选择的节点)减去所有节点的总权重，接着所有节点在加上原始的权重。如上图所示，经过7(节点权重和)轮选择后，又回到了初始状态。
### LeastActive LoadBalance
* **最少活跃调用数**，相同活跃数的随机，活跃数指调用前后计数差。
* 使慢的提供者收到更少请求，因为越慢的提供者的调用前后计数差会越大。

最少活跃数依靠**ActiveLimitFilter**来记录消费者调用的active(只针对当前消费者，不会做整个集群同步，没必要这么做)大小。<br />**思路：**选出活跃数最小的，如果最后选出中有多个活跃数一样的且权重不同，则做权重随机，如果权重一致，则直接从相同权重的提供者中随机。

### ConsistentHash LoadBalance
* **一致性 Hash**，相同参数的请求总是发到同一提供者。
* 当某一台提供者挂时，原本发往该提供者的请求，基于虚拟节点，平摊到其它提供者，不会引起剧烈变动。
* 算法参见：[http://en.wikipedia.org/wiki/Consistent_hashing](http://en.wikipedia.org/wiki/Consistent_hashing)
* 缺省只对第一个参数 Hash，如果要修改，请配置 `<dubbo:parameter key="hash.arguments" value="0,1" />`
* 缺省用 160 份虚拟节点，如果要修改，请配置 `<dubbo:parameter key="hash.nodes" value="320" />`

```java
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        int identityHashCode = System.identityHashCode(invokers);
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        //selector.identityHashCode != identityHashCode 判断提供者是否发生了变化
        if (selector == null || selector.identityHashCode != identityHashCode) {
            //不存在或者节点发送变更，重新创建ConsistentHashSelector，将原来的覆盖
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, identityHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {
        //所有虚节点
        private final TreeMap<Long, Invoker<T>> virtualInvokers;
        //每个真实节点虚拟多少虚节点数目 默认160
        private final int replicaNumber;
        //所有真实提供者hash,校验提供者是否发生更改
        private final int identityHashCode;
        //负载拿几个方法参数做hash,默认1个，则argumentIndex=new int[]{0}
        private final int[] argumentIndex;

        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            this.replicaNumber = url.getMethodParameter(methodName, "hash.nodes", 160);
            //拿几个方法参数来取hash，默认为1个
            String[] index = Constants.COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, "hash.arguments", "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                //每个真实节点做replicaNumber个虚节点，默认为160
                for (int i = 0; i < replicaNumber / 4; i++) {
                    //生成128位(16字节)md5摘要
                    byte[] digest = md5(address + i);
                    for (int h = 0; h < 4; h++) {
                        /**
                         * 将128位分为4部分，0-31，32-63，64-95，96-127(对应digest数组的0-3,4-7,8-11,12-15);取得hash值，作为虚节点的key
                         * 生成4个虚节点，第2个循环将replicaNumber/4
                         * 可能存在冲突hash,不过概率太低了，忽略.dubbo完全可以计算replicaNumber次md5,减少计算次数提高性能
                         */
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public Invoker<T> select(Invocation invocation) {
            String key = toKey(invocation.getArguments());
            byte[] digest = md5(key);
            return selectForKey(hash(digest, 0));
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            //从虚节点中获取大于或等于(最接近)当前hash的节点
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            //如果不存在，则获取首节点
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            /**
             * 1.(digest[3 + number * 4] & 0xFF)
             * 2.(long) (digest[3 + number * 4] & 0xFF)  转为long为了保证第32不管为1还是0 都是正数
             * 3.((long) (digest[3 + number * 4] & 0xFF) << 24) 左移24位后还是正数
             * 这里的取值范围为0~0xFFFFFFFFL 即int无符号位
             */
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes;
            try {
                bytes = value.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.update(bytes);
            return md5.digest();
        }

    }

}

```

一致性hash主要思路首先根据ip或者其他信息为节点生成一个 hash，并将这个 hash 映射到 [0, 2 - 1] 的圆环上。<br />![image.png](https://cdn.nlark.com/yuque/0/2019/png/176230/1547971968758-255442c9-f9cc-4986-92d6-ce4dc0cb1a7d.png#align=left&display=inline&height=289&linkTarget=_blank&name=image.png&originHeight=289&originWidth=377&size=73588&width=377)<br />一致性hash算法做虚节点的主要原因是为了让数据分布更加均匀，避免出现数据倾斜。假设1、2、3三个节点hash后都落在圆环的右侧，那将导致节点1收到过多的流量，不仅造成节点1过大的负载，还会造成数据倾斜。Dubbo目前会将每个真实节点虚拟160个虚拟节点，dubbo虚拟提供者主要是为了均衡各个节点的调用压力。
* 未虚拟节点

![image.png](https://cdn.nlark.com/yuque/0/2019/png/176230/1547973995820-8d98a8f4-4c85-455b-999e-b9b2d50c4b73.png#align=left&display=inline&height=302&linkTarget=_blank&name=image.png&originHeight=604&originWidth=678&size=55024&width=339)
* 虚拟节点

![image.png](https://cdn.nlark.com/yuque/0/2019/png/176230/1547974006573-2438340d-07ab-48ea-8bcc-2456d45538d3.png#align=left&display=inline&height=295&linkTarget=_blank&name=image.png&originHeight=590&originWidth=802&size=84811&width=401)<br /><br /><br />参考<br />[负载均衡](http://dubbo.apache.org/zh-cn/docs/user/demos/loadbalance.html)
