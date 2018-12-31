---
title: redis集群搭建
date: 2016-07-18 10:27:14
tags: redis
---

#### 单机搭建
- 环境:centos6.6 64位

1. 安装redis需要先将官网下载的源码进行编译，编译依赖gcc环境，如果没有gcc环境，需要安装gcc：yum install gcc-c++

<!--more-->

2. 从[官网](http://redis.io/download)下载最新redis源码,目前最新版为3.2.1,将redis-3.2.1.tar.gz拷贝到/usr/local目录下

3. 解压 tar -zxvf redis-3.2.1.tar.gz

4. 进入解压后的目录进行编译
 	cd /usr/local/redis-3.2.1
 	make

5. 安装到指定目录
    cd /usr/local/redis-3.2.1 
    make PREFIX=/usr/local/redis install

#### 启动redis
- 前端启动模式
 /usr/local/redis/bin/redis-server
 默认是前端启动模式，端口是6379
- 后端启动
 1)从redis的源码目录中复制redis.conf到redis的安装目录。
 2)修改配置文件
 将`bind 127.0.0.1 改为 bind 0.0.0.0 `(否则外网客户端无法连接redis,服务器防火墙记得关闭或者开放端口)
   `daemonize no   改为 yes `
 3) ./redis-server redis.conf  
1. 连接redis: ./redis-cli -p 6379

#### redis集群搭建

- 搭建集群需要使用到官方提供的ruby脚本。需要安装ruby的环境。
    安装ruby:
    yum install ruby
    yum install rubygems
    安装ruby和redis的接口程序:[redis-3.0.0.gem](/download/redis-3.0.0.gem)(ps:自己下载)
    执行:gem install redis-3.0.0.gem
    
    
1. mkdir /usr/local/redis-cluster

2. cd /usr/local/redis 

3. 创建6个redis实例:cp -r bin /usr/local/redis-cluster/redis01 (复制6台 01到06) 

4. 修改里面的配置文件redis.conf `端口号`7001到7006 ,`cluster-enabled no`改为yes
                                
5. 复制redis源码包src下的redis-trib.rb到redis-cluster

6. redis-cluster下创建启动脚本startall.sh,命令如下:(也可以手动一个一个启动)
   cd redis01
   ./redis-server redis.conf
   cd ..
   cd redis02
   ./redis-server redis.conf
   cd ..
   cd redis03
   ./redis-server redis.conf
   cd ..
   cd redis04
   ./redis-server redis.conf
   cd ..
   cd redis05
   ./redis-server redis.conf
   cd ..
   cd redis06
   ./redis-server redis.conf
   cd ..
7. chmod +x startall.sh
8. ./startall.sh
9. 创建集群:`./redis-trib.rb create --replicas 1 192.168.2.200:7001 192.168.2.200:7002 192.168.2.200:7003 192.168.2.200:7004 192.168.2.200:7005  192.168.2.200:7006`
  出现: Can I set the above configuration? (type 'yes' to accept):  **yes**(输入yes)   集群就创建完成                         

#### 测试集群:
1. 连接集群:redis01/redis-cli -h 192.168.25.153 -p 7002 -c (连接集群一定要加上-c,其中-c表示以集群方式连接redis，-h指定ip地址，-p指定端口号)

2. 查询集群信息命令: 连接上reids集群,执行cluster nodes

#### 关闭redis 
1. 2种方式关闭redis 
    - 在外面直接使用: ./redis-cli -p 7001 shutdown
    - 连接上redis: redis 192.168.36.189:6379> shutdown
2. 在redis-cluster目录创建集群关闭stopall.sh:
./redis01/redis-cli -p 7001 shutdown
./redis01/redis-cli -p 7002 shutdown
./redis01/redis-cli -p 7003 shutdown
./redis01/redis-cli -p 7004 shutdown
./redis01/redis-cli -p 7005 shutdown
./redis01/redis-cli -p 7006 shutdown
3. chmod +x stopall.sh

4. 关闭集群:./stopall.sh (之后要启动集群直接使用./startall.sh 无需在使用创建集群命令,创建集群命令使用一次成功就行)