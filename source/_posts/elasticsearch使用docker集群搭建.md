---
title: elasticsearch使用docker集群搭建
date: 2018-07-20 16:02:23
tags: elasticsearch
---

elasticsearch2.4.4 + [中文分词器ik](https://github.com/medcl/elasticsearch-analysis-ik)

官方仓库 :`docker pull gude/elasticsearch-ik ` 

腾讯云仓库:`docker pull ccr.ccs.tencentyun.com/gude/elasticsearch-ik`

<!--more-->

## docker运行

### 单机运行:

`docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 gude/elasticsearch-ik`

### 集群：

#### 主节点
```shell
docker run \
  --name some-elasticsearch-master \
  -p 9200:9200 \
  -p 9300:9300 \
  -d \
  gude/elasticsearch-ik
```
#### 从节点
```shell
docker run \
  --name some-elasticsearch-slave1 \
  --link some-elasticsearch-master \
  -p 9201:9200 \
  -p 9301:9300 \
  -d \
  gude/elasticsearch-ik \
  --discovery.zen.ping.unicast.hosts=some-elasticsearch-master
```
#### 集群状态

`curl http://localhost:9200/_cat/health?v`  or  `curl http://localhost:9200/_cluster/health`

##  docker-compose构建集群

```shell
version: '3'
services:
  elasticsearch-master:
    container_name: elasticsearch-master
    image: gude/elasticsearch-ik
    ports:
      - '9200:9200'
      - '9300:9300'
    command:
      - '--network.host=0.0.0.0'
  elasticsearch-slave1:
    container_name: elasticsearch-slave1
    image: gude/elasticsearch-ik
    ports:
      - '9201:9200'
      - '9301:9300'
    command:
      - '--network.host=0.0.0.0'
      - '--discovery.zen.ping.unicast.hosts=elasticsearch-master'
    depends_on:
      - elasticsearch-master
  elasticsearch-slave2:
    container_name: elasticsearch-slave2
    image: gude/elasticsearch-ik
    ports:
     - '9202:9200'
     - '9302:9300'
    command:
     - '--network.host=0.0.0.0'
     - '--discovery.zen.ping.unicast.hosts=elasticsearch-master'
    depends_on:
     - elasticsearch-master
```

## ik分词器使用

1. 创建索引

   `curl -XPUT http://localhost:9200/index`

2. 设置mapping

   ```
   curl -XPOST http://localhost:9200/index/fulltext/_mapping -d'
   {
       "fulltext": {
                "_all": {
               "analyzer": "ik_max_word",
               "search_analyzer": "ik_max_word",
               "term_vector": "no",
               "store": "false"
           },
           "properties": {
               "content": {
                   "type": "string",
                   "analyzer": "ik_max_word",
                   "search_analyzer": "ik_max_word",
                   "include_in_all": "true",
                   "boost": 8
               }
           }
       }
   }'
   ```

   > url中fulltext和json内容的fulltext这2处名称要一直，否则创建mapping报错

   ### ik分词是否生效

   浏览器直接访问

   `http://localhost:9200/_analyze?analyzer=ik&pretty=true&text=我是中国人`

   或者

   ```shell
   curl -XGET localhost:9200/_analyze -d'
   {
     "analyzer": "ik",
     "text": "我是中国人"
   }
   '
   ```

   ik没生效和话，elasticsearch*中文默认*(把analyzer=ik去掉)是一个字一个字进行分词。

   更多细节查看[**elasticsearch-analysis-ik**](https://github.com/medcl/elasticsearch-analysis-ik)

   ***

   **ps:上面集群搭建直接使用elasticsearch官方镜像也可以，本镜像只是添加了ik中文分词器**

   elasticsearch中文分词展示[fastsoso](https://www.fastsoso.cn/)