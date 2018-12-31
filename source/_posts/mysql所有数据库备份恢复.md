---
title: mysql所有数据库备份恢复
date: 2016-04-29 10:14:05
tags: mysql
---

#### 导出所有数据库

```sql
mysqldump -u root -p --opt --all-databases > alldb.sql
```
<!--more-->

#### 导入所有数据库

```sql
mysql -u root -p < alldb.sql
```

[官方文档](http://dev.mysql.com/doc/refman/5.5/en/mysqldump.html)