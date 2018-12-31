---
title: maven打包编译找不到自己添加到lib下的jar包
date: 2016-07-31 17:44:11
tags: maven
---

> 在项目开发中,我们有时需要添加自己的jar又没有放到maven中央仓库中,当我们把jar放到/WEB-INF/lib中时,需要对pom.xml进行配置,要不然编译打包会报错

<!--more-->
具体如下:

```xml
    <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.1</version>
                <configuration>
                    <source>1.7</source>
                    <target>1.7</target>
                    <encoding>UTF-8</encoding>
                    <compilerArguments>
                        <extdirs>${project.basedir}/src/main/webapp/WEB-INF/lib</extdirs>
                    </compilerArguments>
                </configuration>
    </plugin>
```
只需添加` <extdirs>${project.basedir}/src/main/webapp/WEB-INF/lib</extdirs>`