---
title: 学习dubbo源码-Compile动态编译
date: 2018-02-19 20:17:03
tags: dubbo
---

Compiler是一个SPI扩展点，他实现有
```java
adaptive=com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler
jdk=com.alibaba.dubbo.common.compiler.support.JdkCompiler
javassist=com.alibaba.dubbo.common.compiler.support.JavassistCompiler
```
默认实现是javassist

#### javassist
[Javassist](https://github.com/jboss-javassist/javassist)是一个开源的分析、编辑和创建Java字节码的类库,它可以使我们对于字节码的操作变的更加的简单。

<!--more-->

##### 例子
```java
public class JavassistDemo {
    public static void main(String[] args) throws Exception {
        // 1、创建ClassPool：CtClass对象的容器
        ClassPool pool = ClassPool.getDefault();

        // 2、通过ClassPool生成一个public新类Hello.java
        CtClass ctClass = pool.makeClass("com.gude.Hello");
        //ctClass.setSuperclass(); 父类设置
        //ctClass.setInterfaces(); 接口设置

        // 3、添加属性
        CtField nameField = new CtField(pool.getCtClass("java.lang.String"),
                "name", ctClass);
        //设为私有
        nameField.setModifiers(Modifier.PRIVATE);
        ctClass.addField(nameField);
        //4、添加get set
        ctClass.addMethod(CtNewMethod.getter("getName", nameField));
        ctClass.addMethod(CtNewMethod.setter("setName", nameField));
        //5、添加自定义方法
        CtMethod ctMethod = new CtMethod(CtClass.voidType, "customMethod",
                new CtClass[]{}, ctClass);
        ctMethod.setModifiers(Modifier.PUBLIC);
        ctMethod.setBody("System.out.println(\"custome method\");");
        ctClass.addMethod(ctMethod);
        //6、添加Hello(String name)有参构造方法
        CtConstructor ctConstructor = new CtConstructor(new CtClass[]{pool.getCtClass("java.lang.String")}, ctClass);
        ctConstructor.setBody("{this.name=name;}");
        ctClass.addConstructor(ctConstructor);
        //无参构造
        CtConstructor cons = new CtConstructor(null, ctClass);
        cons.setBody("{}");
        ctClass.addConstructor(cons);

        //7、生成一个class
        Class<?> clazz = ctClass.toClass();
        
        //测试
        Object o = clazz.newInstance();
        clazz.getDeclaredMethod("customMethod").invoke(o);

    }
}

```


> ExtensionLoader类创建自适应扩展类核心代码 
 
```java
 private Class<?> createAdaptiveExtensionClass() {
        String code = createAdaptiveExtensionClassCode();
        ClassLoader classLoader = findClassLoader();
        com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
}

```
1. 获取自适应扩展，也就是AdaptiveCompiler
2. 调用AdaptiveCompiler的compile方法
  ```java
   public Class<?> compile(String code, ClassLoader classLoader) {
        Compiler compiler;
        ExtensionLoader<Compiler> loader = ExtensionLoader.getExtensionLoader(Compiler.class);
        String name = DEFAULT_COMPILER; // copy reference
        if (name != null && name.length() > 0) {
            compiler = loader.getExtension(name);
        } else {
            //默认返回Javassist实现
            compiler = loader.getDefaultExtension();
        }
        //默认的情况下会调用Javassist的compile方法
        return compiler.compile(code, classLoader);
    }
  ```
3. JavassistCompiler和JdkCompiler继承自AbstractCompiler
   - AbstractCompiler 首先根据拼接的代码code找出所需编译的类，尝试去加载当前类，如果加载成功直接返回，否则使用Javassist或者Jdk去编译源码（doCompile方法）
   ```java
    public Class<?> compile(String code, ClassLoader classLoader) {
        code = code.trim();
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        String pkg;
        if (matcher.find()) {
            pkg = matcher.group(1);
        } else {
            pkg = "";
        }
        matcher = CLASS_PATTERN.matcher(code);
        String cls;
        if (matcher.find()) {
            cls = matcher.group(1);
        } else {
            throw new IllegalArgumentException("No such class name in " + code);
        }
        String className = pkg != null && pkg.length() > 0 ? pkg + "." + cls : cls;
        try {
            return Class.forName(className, true, ClassHelper.getCallerClassLoader(getClass()));
        } catch (ClassNotFoundException e) {
            if (!code.endsWith("}")) {
                throw new IllegalStateException("The java code not endsWith \"}\", code: \n" + code + "\n");
            }
            try {
                return doCompile(className, code);
            } catch (RuntimeException t) {
                throw t;
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to compile class, cause: " + t.getMessage() + ", class: " + className + ", code: \n" + code + "\n, stack: " + ClassUtils.toString(t));
            }
        }
    }
   ```
   - JavassistCompiler 通过正则匹配导入的包、继承、接口、方法、属性，然后使用Javassist来创建字节码，返回成功创建的类
   ```java
    public Class<?> doCompile(String name, String source) throws Throwable {
        int i = name.lastIndexOf('.');
        String className = i < 0 ? name : name.substring(i + 1);
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(ClassHelper.getCallerClassLoader(getClass())));
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        List<String> importPackages = new ArrayList<String>();
        Map<String, String> fullNames = new HashMap<String, String>();
        while (matcher.find()) {
            String pkg = matcher.group(1);
            if (pkg.endsWith(".*")) {
                String pkgName = pkg.substring(0, pkg.length() - 2);
                pool.importPackage(pkgName);
                importPackages.add(pkgName);
            } else {
                int pi = pkg.lastIndexOf('.');
                if (pi > 0) {
                    String pkgName = pkg.substring(0, pi);
                    pool.importPackage(pkgName);
                    importPackages.add(pkgName);
                    fullNames.put(pkg.substring(pi + 1), pkg);
                }
            }
        }
        String[] packages = importPackages.toArray(new String[0]);
        matcher = EXTENDS_PATTERN.matcher(source);
        CtClass cls;
        if (matcher.find()) {
            String extend = matcher.group(1).trim();
            String extendClass;
            if (extend.contains(".")) {
                extendClass = extend;
            } else if (fullNames.containsKey(extend)) {
                extendClass = fullNames.get(extend);
            } else {
                extendClass = ClassUtils.forName(packages, extend).getName();
            }
            cls = pool.makeClass(name, pool.get(extendClass));
        } else {
            cls = pool.makeClass(name);
        }
        matcher = IMPLEMENTS_PATTERN.matcher(source);
        if (matcher.find()) {
            String[] ifaces = matcher.group(1).trim().split("\\,");
            for (String iface : ifaces) {
                iface = iface.trim();
                String ifaceClass;
                if (iface.contains(".")) {
                    ifaceClass = iface;
                } else if (fullNames.containsKey(iface)) {
                    ifaceClass = fullNames.get(iface);
                } else {
                    ifaceClass = ClassUtils.forName(packages, iface).getName();
                }
                cls.addInterface(pool.get(ifaceClass));
            }
        }
        String body = source.substring(source.indexOf("{") + 1, source.length() - 1);
        String[] methods = METHODS_PATTERN.split(body);
        for (String method : methods) {
            method = method.trim();
            if (method.length() > 0) {
                if (method.startsWith(className)) {
                    cls.addConstructor(CtNewConstructor.make("public " + method, cls));
                } else if (FIELD_PATTERN.matcher(method).matches()) {
                    cls.addField(CtField.make("private " + method, cls));
                } else {
                    cls.addMethod(CtNewMethod.make("public " + method, cls));
                }
            }
        }
        return cls.toClass(ClassHelper.getCallerClassLoader(getClass()), JavassistCompiler.class.getProtectionDomain());
    }
   ```
   - JdkCompiler
   原生的jdk自带的动态编译类库`JavaCompiler`,从1.6开始提供 [文档](http://tool.oschina.net/uploads/apidocs/jdk-zh/javax/tools/JavaCompiler.html)