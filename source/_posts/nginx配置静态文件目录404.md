---
title: nginx配置静态文件目录404
date: 2017-01-22 12:24:46
tags: nginx
---

今天使用nginx配置静态文件代理时，访问一直是404，罪魁祸首就是我没搞清楚root与alias的区别。

<!--more-->
#### 错误

一开始配置：
```xml
location /static/ {
	    root /home/static;
}
```
> 当我访问/static/a.css这样实际上要到/home/static/static/下找文件，而我的目的是让/static/去访问/home/static/下的文件，这样就造成了404

#### 解决

```xml
location /static/ {
	    root /home;
}
```
或者
```xml
location /static/ {
	    alias /home/static/;
}
```

#### alias与root区别
- 官方root
```
Sets the root directory for requests. For example, with the following configuration
location /i/ {
    root /data/w3;
}
```
The **/data/w3/i/top.gif** file will be sent in response to the **“/i/top.gif”** request
- 官方alias
```
Defines a replacement for the specified location. For example, with the following configuration
location /i/ {
    alias /data/w3/images/;
}
```
on request of **“/i/top.gif”**, the file **/data/w3/images/top.gif** will be sent.

> 当访问/i/top.gif时，root是去/data/w3/i/top.gif请求文件，alias是去/data/w3/images/top.gif请求,也就是说
root响应的路径：**配置的路径+完整访问路径(完整的location配置路径+静态文件)**
alias响应的路径：**配置路径+静态文件(去除location中配置的路径)**

#### 注意

1. 使用alias时目录名后面一定要加“/”
2. 一般情况下，在location /中配置root，在location /other中配置alias