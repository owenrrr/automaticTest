# automaticTest
自动化测试大作业

**利用HashSet来去重，尽量减低复杂度及需要计算的方法，故exclusion.txt多加了几个正则表达式优先过滤**

**程序入口：src/main/java/net/mooctest/Analy.java: main(String[] args)**
```
- createMethodDotFile(String target)        
- createClassDotFile(String path)
- createSelectMethod(String path)
- createSelectClass()
- isNetMooctest(String class)
- readChangeInfo(String path)
```
>createMethodDotFile(String target) **创建method-cfa.dot**
>createClassDotFile() **创建class-cfa.dot**
>createSelectMethod(String path) **创建selection-method.txt**
>createSelectClass(String path) **创建selection-class.txt**
>isNetMooctest(String class) **判断是否在net.mooctest包下**
>readChangeInfo(String path) **读取change_info.txt，并返回Map<String, HashSet>**

**由于程序运行过程容易出错，故在程序中添加断言assert，如：**
```
assert tmp.length == 2;
```
**或利用try catch来捕获异常，判断程序正确性及增加调试效率**
