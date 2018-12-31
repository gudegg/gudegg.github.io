---
title: Datasource和DriverManager获取Connection的区别
date: 2017-12-03 15:14:23
tags: 数据库
---

- Datasource是一个接口，DriverManager是具体的类。接口可以很多种实现方式，这正是java多态的特性。
- Datasource会帮我们创建、管理和分配数据库连接，它有多种实现，如：dbcp、Druid、HikariCP等。

> 接下来看[HikariCP](https://github.com/brettwooldridge/HikariCP)是如何获取连接的：通过阅读HikariCP的源码，发现它在初始化的时候最终获取Connection是调用`driver.connect(jdbcUrl, driverProperties);`,而DriverManager获取Connection也是调用`driver.connect(url, info);`。具体的连接由数据库的驱动实现。
  
<!--more-->
- DriverManager.getConnection("")调用最终会走到如下代码
```java
//获取注册的驱动进行 如：Class.forName("")注册驱动
for(DriverInfo aDriver : registeredDrivers) {
            // If the caller does not have permission to load the driver then
            // skip it.
            if(isDriverAllowed(aDriver.driver, callerCL)) {
                try {
                    println("    trying " + aDriver.driver.getClass().getName());
                    Connection con = aDriver.driver.connect(url, info);
                    if (con != null) {
                        // Success!
                        println("getConnection returning " + aDriver.driver.getClass().getName());
                        return (con);
                    }
                } catch (SQLException ex) {
                    if (reason == null) {
                        reason = ex;
                    }
                }

            } else {
                println("    skipping: " + aDriver.getClass().getName());
            }

        }
```
- HikariCP初始化会走到DriverDataSource类的getConnection方法
```java
   @Override
   public Connection getConnection() throws SQLException
   {
      return driver.connect(jdbcUrl, driverProperties);
   }
```
#### 总结
**Datasource和DriverManager最终获取Connection的方式都是一样的**，都是通过driver.connect()获取，只不过Datasource(指具体的连接池实现)会在初始化的帮我们创建一定数量的连接,这样就不需要每次都去创建Connection，需要使用的时候们只要去池中取未被使用的连接就可以，使用完的连接会被自动释放。而DriverManager需要每次都去创建，这样会耗费内存和时间(除非你只需一个连接就能搞定)。因此，我们在编写项目的时候，都推荐使用了连接池。
