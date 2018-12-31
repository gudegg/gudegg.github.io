---
title: 配置tomcat直接以根目录访问
date: 2016-06-04 16:54:35
tags: tomcat
---

#### 法1
- 直接将*.war改名ROOT.war放到webapps下

<!--more-->

#### 法2

- 修改文件“conf/server.xml”，在Host节点下增加如下`<Context>`的内容配置：

```xml
 <Host name="localhost"  appBase="webapps" unpackWARs="true" autoDeploy="true"
     xmlValidation="false" xmlNamespaceAware="false">
     ......
 <Context path="" docBase="D:/tomcat/myapps/gude.war"></Context>
 </Host>
```
注意：
    1）path 的值设置为空；
    2）应用不要放到tomcat的webapps目录下(如上述配置是放到自定义的文件夹myapps内的)，否则访问时路径很有问题；
    3）docBase指定到绝对路径。
    如此设置后重启tomcat，如果docBase指向的是war文件，会自动将war解压到webapps/ROOT目录；如果docBase指向的是应用已解压好的目录，如 docBase="D:/tomcat/myapps/gude"，tomcat不会生成webapps/ROOT目录（这种情况下之前可以不用删除webapps/ROOT目录，但webapps/ROOT目录内的内容是无法访问的），访问时将直接使用docBase指定的目录。