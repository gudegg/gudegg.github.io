---
title: 'IntelliJ IDEA maven打包报错：Fatal error compiling: 无效的目标发行版: 1.8 -> [Help 1]'
date: 2017-04-20 13:44:11
tags: maven
---

> 今天使用IntelliJ IDEA准备对spring-boot开发项目进行打包时，一直报：** Fatal error compiling: 无效的目标发行版: 1.8 -> [Help 1] **,但是我的系统环境全部是jdk 1.8,百思不得其解

<!--more-->

#### 原因
IntelliJ IDEA中maven会自己对jdk环境进行设置，导致IDEA中的maven使用的jdk是1.7
#### 解决
IntelliJ IDEA中打开File > Settings > Build > Build Tools > Maven > Runner ,将JRE版本设为1.8就能完美解决