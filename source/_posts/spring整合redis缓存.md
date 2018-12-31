---
title: spring整合redis缓存
date: 2016-07-18 11:39:08
tags: redis
---

1. 引入jar包

```xml
        <dependency>
            <groupId>org.springframework.data</groupId>
            <artifactId>spring-data-redis</artifactId>
            <version>1.7.2.RELEASE</version>
        </dependency>
        <dependency>
            <groupId>redis.clients</groupId>
            <artifactId>jedis</artifactId>
            <version>2.8.0</version>
        </dependency>
```
<!--more-->

2. 配置bean(ps:集群在spring-data-redis 1.7.0之后才支持)

```xml
<!--redis缓存配置 纯代码配置也行-->
    <bean id="userKeyGenerator" class="org.springframework.cache.interceptor.SimpleKeyGenerator"/>
    <bean id="redisClusterConfiguration" class="org.springframework.data.redis.connection.RedisClusterConfiguration">
        <constructor-arg name="clusterNodes">
            <list>
                <value>192.168.2.200:7001</value>
                <value>192.168.2.200:7002</value>
                <value>192.168.2.200:7003</value>
                <value>192.168.2.200:7004</value>
                <value>192.168.2.200:7005</value>
                <value>192.168.2.200:7006</value>
            </list>
        </constructor-arg>
    </bean>


    <bean name="connectionFactory" class="org.springframework.data.redis.connection.jedis.JedisConnectionFactory">
        <!--单机-->
        <!--<property name="hostName" value="${redis.host1}"/>-->
        <!--<property name="port" value="${redis.port1}"/>-->
        <!--<property name="password" value="123456"/>-->
        <!--集群-->
        <constructor-arg name="clusterConfig" ref="redisClusterConfiguration"/>
    </bean>
    <bean name="redisTemplate2" class="org.springframework.data.redis.core.RedisTemplate">
        <property name="connectionFactory" ref="connectionFactory"/>
    </bean>
    <bean name="cacheManager2" class="org.springframework.data.redis.cache.RedisCacheManager">
        <constructor-arg name="redisOperations" ref="redisTemplate2"/>
        <!--设置过期时间300s 默认为0,不过期-->
        <property name="defaultExpiration" value="300"/>
        <!--可以为单独缓存value设置过期时间-->
        <property name="expires">
            <map>
                <entry key="defaultCache" value="3600"/>
            </map>
        </property>
    </bean>
    <cache:annotation-driven cache-manager="cacheManager2" proxy-target-class="true" key-generator="userKeyGenerator"/>
```

3.在需要使用缓存的地方使用@CachePut @Cacheable @CacheEvict就可使用redis缓存

[整合小例子](https://github.com/gudegg/spring-redis.git)