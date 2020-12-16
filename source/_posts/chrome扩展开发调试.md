---
title: chrome扩展开发调试
date: 2016-03-22 16:47:47
tags: chrome扩展开发
---

#### 调试Content Script

- **content_scripts**的js代码可以直接在chrome的调试控制台下找到(Sources-->Content script)

![content](https://gitee.com/zhangguodong/image/raw/master/picgo/content.png)

<!--more-->



#### 调试popup

- 选择插件右键点击审查弹出内容,如果需要对立面的js进行调试,打上断点后在控制台输入**命令: `location.reload(true)` ** 来重新加载这个页面.

![popum](https://gitee.com/zhangguodong/image/raw/master/picgo/popum.png)



#### 调试Background

- 打开chrome扩展程序管理界面,找到需要调试的有Background的插件 ,打开**检查视图**的页面
![background](https://gitee.com/zhangguodong/image/raw/master/picgo/background.png)