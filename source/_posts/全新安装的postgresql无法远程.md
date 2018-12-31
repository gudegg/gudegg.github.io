---
title: 全新安装的postgresql无法远程连接
date: 2016-04-17 09:08:09
tags: postgresql
---


> postgresql默认情况下只允许本机进行访问,无法远程访问,要想进行远程访问,需要到postgresql**安装目录**的(系统分区\PostgreSQL\版本号\data)下的**pg_hba.conf**进行修改.

<!--more-->
- 如果要让所有远程ip都能访问就修改# IPv4 local connections:下的内容为`host    all             all             0.0.0.0/0            md5`
- 如果只是想允许某台远程电脑访问,只需添加`host    all             all             你的电脑ip/32  trust`