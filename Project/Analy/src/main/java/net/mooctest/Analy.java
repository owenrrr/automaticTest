package net.mooctest;

import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.*;
/**
 * @Author: Owen
 * @Date: 2020/11/15 21:32
 * @Description:
 */
public class Analy {
    public static ClassLoader WALA_CLASS_LOADER = Analy.class.getClassLoader();
    public static final String EXCLUSION_FILE = "exclusion.txt";

    public static void main(String[] args) {
        try {
            String command = args[0];
            String target = args[1];
            String changeInfo = args[2];
//            String command = "-m";
//            String target = "C:\\automaticTest\\target";
//            String changeInfo = "C:\\automaticTest\\data\\change_info.txt";

            if (!command.equals("-c") && !command.equals("-m")){
                System.out.println("Command not found.");
            }else{
                if (target.charAt(target.length()-1)=='\\'){
                    target = target.substring(0,target.length()-1);
                }
                createMethodDotFile(target);
                createClassDotFile(target);
                if (command.equals("-c")){
                    createSelectClass(target,changeInfo);
                }else{
                    createSelectMethod(target,changeInfo);
                }
            }
        }catch(Exception e){
            System.out.println(e);
        }
    }

    public static void createSelectClass(String target,String path){

        try{
            Map<String, HashSet<String>> changeInfo = readChangeInfo(path);
            String str, line, caller_nofilter, callee_nofilter, caller_class, callee_class;
            String[] tmp;
            HashSet<String> classSet = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> entry : changeInfo.entrySet()) {
                classSet.add(entry.getKey());
            }
            int count;
            // 读取class-cfa.dot
//            BufferedReader classReader = new BufferedReader(new FileReader(target.substring(0,target.length()-7) + "\\data\\class-cfa.dot"));
            BufferedReader classReader = new BufferedReader(new FileReader("class-cfa.dot"));

            HashSet<String> tmpSet = new HashSet<>();
            do {
                count = 0;
                while ((line = classReader.readLine()) != null){
                    if (line.equals("digraph class {") || line.equals("}")){
                        continue;
                    }
                    line = line.substring(1,line.length()-1);
                    tmp = line.split(" -> ");

                    assert tmp.length == 2;

                    caller_nofilter = tmp[0].substring(1,tmp[0].length()-1);
                    callee_nofilter = tmp[1].substring(1,tmp[1].length()-1);

                    if (classSet.contains(caller_nofilter)){
                        if (!classSet.contains(callee_nofilter)){
                            classSet.add(callee_nofilter);
                            count++;
                        }
                    }
                }
            }while (count != 0);

            // 读取method-cfa.dot
            BufferedReader methodReader = new BufferedReader(new FileReader("method-cfa.dot"));
            // 写selection-class.txt
            FileWriter writer = new FileWriter(new File("selection-class.txt"));
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            HashSet<String> resultSet = new HashSet<>();

            while ((line = methodReader.readLine()) != null){
                if (line.equals("digraph method {") || line.equals("}")){
                    continue;
                }
                line = line.substring(1,line.length()-1);
                tmp = line.split(" -> ");

                assert tmp.length == 2;

                caller_nofilter = tmp[0].substring(1,tmp[0].length()-1);
                callee_nofilter = tmp[1].substring(1,tmp[1].length()-1);
                tmp = caller_nofilter.split("\\.");
                caller_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];
                tmp = callee_nofilter.split("\\.");
                callee_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];

                if (classSet.contains(caller_class)){
                    if (callee_nofilter.contains("Test") && !callee_nofilter.contains("<init>()")){
                        resultSet.add(callee_nofilter);
                    }
                }
            }

            Iterator iterator = resultSet.iterator();
            String resu, resu_class;
            while (iterator.hasNext()){
                resu = (String) iterator.next();
                tmp = resu.split("\\.");
                resu_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];
                bufferedWriter.write(String.format("%s %s\r\n",resu_class, resu));
                bufferedWriter.flush();
            }
            bufferedWriter.close();
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
        System.out.println("select-class.txt created!");
    }

    public static void createSelectMethod(String target,String path){
        Map<String, HashSet<String>> hashMap = new HashMap<>();
        HashSet<String> set = new HashSet<>();
        String line, caller_class, callee_class, caller_nofilter, callee_nofilter, str;
        String[] tmp;

        Map<String, HashSet<String>> changeInfo = readChangeInfo(path);
        List<String> removeList = new ArrayList<>();   //y用于最后排除掉初始的changeInfo
        List<String> classList = new ArrayList<>();
        HashSet<String> methodSet = new HashSet<>();

        try{
            for (Map.Entry<String, HashSet<String>> entry : changeInfo.entrySet()) {
                Iterator iterator = entry.getValue().iterator();
                while (iterator.hasNext()){
                    str = (String) iterator.next();
                    removeList.add(str);
                    methodSet.add(str);
                }
            }
            // 读取method-CMD-cfa-test.dot
            BufferedReader reader;

            // 写select-class-test.txt
            FileWriter writer = new FileWriter(new File("selection-method.txt"));
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            HashSet<String> tmpSet = new HashSet<>();
            String next;
            int counter;  // 用于计算本次循环是否还有关联其他方法，若无则在下次循环中跳出

            do {
                counter = 0;
                tmpSet = new HashSet<>();
                reader = new BufferedReader(new FileReader("method-cfa.dot"));
                while ((line = reader.readLine()) != null) {
                    if (line.equals("digraph method {") || line.equals("}")){
                        continue;
                    }
                    line = line.substring(1,line.length()-1);
                    tmp = line.split(" -> ");

                    assert tmp.length == 2;

                    caller_nofilter = tmp[0].substring(1,tmp[0].length()-1);
                    callee_nofilter = tmp[1].substring(1,tmp[1].length()-1);
                    tmp = caller_nofilter.split("\\.");  //  0:net ;1:mooctest; 2:类名
                    caller_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];
                    tmp = callee_nofilter.split("\\.");
                    callee_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];

//                    if (methodSet.contains(caller_nofilter) && !callee_class.equals(caller_class)){
                    if (methodSet.contains(caller_nofilter)){
                        tmpSet.add(callee_nofilter);
                    }
                }

                Iterator iterator = tmpSet.iterator();
                while (iterator.hasNext()){
                    next = (String) iterator.next();
                    if (methodSet.contains(next)){
                        continue;
                    }
                    methodSet.add(next);
                    counter++;
                }
            }while(counter != 0);

            // 最终输出，但要排除初始changeInfo
            for (int i=0; i<removeList.size(); i++){
                assert methodSet.remove(removeList.get(i));
            }

            for (int i=0; i<removeList.size(); i++){
                str = removeList.get(i);
                tmp = str.split("\\.");
                classList.add("L"+tmp[0]+"/"+tmp[1]+"/"+tmp[2]);
            }
            Iterator iterator = methodSet.iterator();
            while (iterator.hasNext()){
                next = (String) iterator.next();
                tmp = next.split("\\.");
                if (tmp[2].contains("Test") && !next.contains("<init>()")){
                    str = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];
                    if (!classList.contains(str)){
                        bufferedWriter.write(String.format("%s %s\r\n",str, next));
                        bufferedWriter.flush();
                    }
                }
            }
            bufferedWriter.close();
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
        System.out.println("select-method.txt created!");
    }

    public static void createMethodDotFile(String target) throws Exception{

        AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", new File(EXCLUSION_FILE), WALA_CLASS_LOADER);

        File[] classes = new File(target+ "\\classes\\net\\mooctest").listFiles();
        File[] test_classes = new File(target+"\\test-classes\\net\\mooctest").listFiles();

        for (int i=0; i<classes.length; i++){
            if (!classes[i].toString().substring(classes[i].toString().length()-11).equals("Analy.class")){
                System.out.println(classes[i].toString());
                scope.addClassFileToScope(ClassLoaderReference.Application, new File(classes[i].toString()));
            }
        }
        for (int i=0; i<test_classes.length; i++){
            System.out.println(test_classes[i].toString());
            scope.addClassFileToScope(ClassLoaderReference.Application, new File(test_classes[i].toString()));
        }

        // 1.生成类层次关系对象
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        // 2.生成进入点
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        // 3.利用CHA算法构建调用图
        CHACallGraph cg = new CHACallGraph(cha);
        cg.init(eps);
        System.out.println("---CHACallGraph Completed---");

//        File file = new File(target.substring(0,target.length()-7) + "\\data\\method-cfa.dot");
        File file = new File("method-cfa.dot");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);
        bufferedWriter.write("digraph method {\r\n");

        // 4.遍历cg中所有的节点
        for (CGNode node : cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if (node.getMethod() instanceof ShrikeBTMethod) {
                if ("Application".equals(node.getMethod().getDeclaringClass().getClassLoader().toString())){
                    Iterator<CGNode> iterator = cg.getPredNodes(node);
                    while(iterator.hasNext()){
                        CGNode tmp = iterator.next();
                        if (tmp.getMethod() instanceof ShrikeBTMethod){
                            if ("Application".equals(tmp.getMethod().getDeclaringClass().getClassLoader().toString())){
                                if (isNetMooctest(tmp.getMethod().getSignature()) && isNetMooctest(node.getMethod().getSignature())){
                                    bufferedWriter.write(String.format("\t\"%s\" -> \"%s\";\r\n",node.getMethod().getSignature(),tmp.getMethod().getSignature()));
                                }
                            }
                        }
                    }
                    bufferedWriter.flush();
                }
            }else{
            }
        }
        bufferedWriter.write("}\r\n");
        bufferedWriter.flush();
        bufferedWriter.close();

        System.out.println("method-cfa.dot created!");
        return;
    }

    public static void createClassDotFile(String target){
        // 通过读取method.dot文件来生成class.dot
        Map<String,HashSet<String>> hashMap = new HashMap<>();
        String line, caller_class, callee_class;
        HashSet<String> set = new HashSet<>();  //hashset不会重复
        String[] tmp;
        try {
            BufferedReader reader = new BufferedReader(new FileReader("method-cfa.dot"));
            while ((line = reader.readLine()) != null) {
                if (line.equals("digraph method {") || line.equals("}")){
                    continue;
                }
                line = line.substring(1,line.length()-1);  //去除首部\t,尾部;
                tmp = line.split(" -> ");
                if (tmp.length != 2){
                    System.out.println("method.dot format is wrong");
                    break;
                }
                //去除首尾""
                caller_class = tmp[0].substring(1,tmp[0].length()-1);
                callee_class = tmp[1].substring(1,tmp[1].length()-1);
                tmp = caller_class.split("\\.");  //  0:net ;1:mooctest; 2:类名
                caller_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];
                tmp = callee_class.split("\\.");
                callee_class = "L" + tmp[0] + "/" + tmp[1] + "/" + tmp[2];

                if (hashMap.get(caller_class) != null){
                    set = hashMap.get(caller_class);
                    set.add(callee_class);
                    hashMap.put(caller_class,set);
                    set = new HashSet<>();
                }else{
                    set.add(callee_class);
                    hashMap.put(caller_class,set);
                    set = new HashSet<>();
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        try{
            FileWriter writer = new FileWriter(new File("class-cfa.dot"));
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write("digraph class {\r\n");
            Iterator iterator;
            String valueTmp, keyTmp;
            //由hashMap来生成class.dot
            for (Map.Entry<String, HashSet<String>> entry : hashMap.entrySet()) {
                keyTmp = entry.getKey();
                iterator = entry.getValue().iterator();
                while (iterator.hasNext()){
                    valueTmp = (String) iterator.next();
                    bufferedWriter.write(String.format("\t\"%s\" -> \"%s\";\r\n",keyTmp, valueTmp));
                    bufferedWriter.flush();
                }
            }
            bufferedWriter.write("}\r\n");
            bufferedWriter.flush();
            bufferedWriter.close();
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
        System.out.println("class-cfa.dot created!");
    }

    public static boolean isNetMooctest(String string){
        try{
            if (string.substring(0,12).equals("net.mooctest")){
                return true;
            }
        }catch(Exception e){
            System.out.println("length is less than 11");
            return false;
        }
        return false;
    }

    public static Map<String, HashSet<String>> readChangeInfo(String changeInfo) {
        Map<String, HashSet<String>> hashMap = new HashMap<String, HashSet<String>>();
        HashSet<String> set = new HashSet<>();
        String line;
        String[] tmp;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(changeInfo));
            while ((line = reader.readLine()) != null) {
                tmp = line.split(" ");
                if (tmp.length != 2) {
                    System.out.println("change_info.txt format is wrong");
                    break;
                }
                //索引0存放类，索引1存放方法
                if (hashMap.get(tmp[0]) != null) {
                    // key存在
                    set = hashMap.get(tmp[0]);
                    set.add(tmp[1]);
                    hashMap.put(tmp[0], set);
                    set = new HashSet<>();
                } else {
                    set.add(tmp[1]);
                    hashMap.put(tmp[0], set);
                    set = new HashSet<>();
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return hashMap;
    }

}
