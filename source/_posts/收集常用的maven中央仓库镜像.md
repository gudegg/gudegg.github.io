---
title: 收集常用的maven中央仓库镜像
date: 2016-06-04 17:03:19
tags: maven
---

由于maven中央仓库在国外,国内访问的速度非常的慢,收集几个速度还算不错的镜像

**镜像列表:**

1. http://maven.aliyun.com/nexus/content/groups/public/ (阿里云 包不全 但是速度快 一般的包都有)
1. http://uk.maven.org/maven2/ (推荐)
1. http://maven.antelink.com/content/groups/public/ (推荐)
1. https://maven-central.storage.googleapis.com/ (有些地区被墙)
1. https://repo.spring.io/plugins-release/

<!--more-->
在(maven安装路径/config/settings.xml)中进行如下修改:
```xml
<settings>
  ...
  <mirrors>
    <mirror>
      <id>UK</id>
      <name>UK Central</name>
      <url>http://uk.maven.org/maven2</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
  ...
</settings>
```

Takari公司，[http://takari.io](http://takari.io),也即Maven创始人Jason van Zyl创建的公司，最近宣布在Google Cloud Storage上建立了Maven镜像仓库，开放给全球所有的开发者使用。主流的依赖管理工具如Maven、Apache Ivy、Gradle等都可以使用此中央仓库。
要使用此Maven镜像中央仓库，只需在Maven的settings.xml配置文件中，修改节的内容即可，如下：
```xml
 <mirrors>
    <mirror>
      <id>google-maven-central</id>
      <name>Google Maven Central</name>
      <url>https://maven-central.storage.googleapis.com</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
```