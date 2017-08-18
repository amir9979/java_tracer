package inst;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ClassLoader;
import java.lang.Class;
import java.util.Arrays;
import java.util.ArrayList;

import java.security.ProtectionDomain;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javassist.CannotCompileException;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import java.net.URLClassLoader;

public class MyInstrumenter implements ClassFileTransformer {

	public boolean init = false;
	public static String injectedClassName = null;

	public void initiate(String name, ClassLoader loader, ProtectionDomain protectionDomain) {
		if (!init) {
			init = true;
			injectedClassName = "junit.framework.TestCase";
			//injectedClassName=name;
			ClassPool cp = ClassPool.getDefault();

			cp.importPackage("java.io.PrintWriter");
			cp.importPackage("java.io.BufferedWriter");
			cp.importPackage("java.io.FileWriter");
			cp.importPackage("java.io.File");
			cp.importPackage("java.io.IOException");
			cp.importPackage("java.lang.StringBuffer");
			cp.importPackage("java.net.URLClassLoader");
			cp.importPackage("java.net.URL");
			cp.importPackage(injectedClassName);
			cp.importPackage("java.util.LinkedList");
			cp.importPackage("java.util.List");

			System.out.println("Injecting method to " + injectedClassName + "...");
			//injectToMethod(loader,protectionDomain);	
			System.out.println("Method injection done");

			ClassPathForGeneratedClasses gcp = new ClassPathForGeneratedClasses();
			cp.insertClassPath(gcp);

			System.out.println("Hello!" + System.currentTimeMillis());

			try {
				CtClass traceWriterClass = cp.makeClass("TestsTraces");
				CtField fileNameField;
				fileNameField = CtField.make("public static String fileName=null;", traceWriterClass);
				fileNameField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
				traceWriterClass.addField(fileNameField, "null");

				CtField testClassField;
				testClassField = CtField.make("public static String testClass=null;", traceWriterClass);
				testClassField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
				traceWriterClass.addField(testClassField, "null");

				CtField outField;
				outField = CtField.make("public static java.io.PrintWriter out=null;", traceWriterClass);
				outField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
				traceWriterClass.addField(outField, "null");

				CtField qField;
				qField = CtField.make("public static java.util.LinkedList q = new java.util.LinkedList();",
						traceWriterClass);
				qField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
				traceWriterClass.addField(qField, "new java.util.LinkedList()");

				StringBuilder changeFile = new StringBuilder();
				changeFile.append("public static synchronized void changefile(java.lang.String newName){\n");
				//changeFile.append("if(TestsTraces.fileName==null){\n");
				changeFile.append("long time= System.currentTimeMillis();\n");
				changeFile.append("String name=newName;\n");
				changeFile.append("String namePre=\"..\\\\..\\\\DebuggerTests\\\\Trace_\"+name;\n");
				changeFile.append(
						"boolean check =TestsTraces.fileName!=null && namePre.equals(TestsTraces.fileName.substring(0, TestsTraces.fileName.lastIndexOf(\"_\")));\n");
				changeFile.append("if( check){ return; }\n");
				changeFile.append(
						"TestsTraces.fileName=\"..\\\\..\\\\DebuggerTests\\\\Trace_\"+name+\"_\"+time+\".txt\";\n");
				changeFile.append(
						"File f=new File(TestsTraces.fileName.substring(0, TestsTraces.fileName.lastIndexOf(\"\\\\\")));\n");
				//changeFile.append("f=new File(f.getPath());\n");
				//changeFile.append("System.out.println(\"dir: \" +f.getPath());\n");
				changeFile.append("f.mkdirs();\n");
				changeFile.append("System.out.println(\"changed! \" +TestsTraces.fileName);\n");
				changeFile.append(
						"TestsTraces.testClass=TestsTraces.fileName.substring(0, TestsTraces.fileName.lastIndexOf('.')); TestsTraces.q = new java.util.LinkedList();\n");
				changeFile.append("try {\n");
				changeFile.append(
						"TestsTraces.out = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(TestsTraces.fileName, true)));\n");
				changeFile.append("} catch (IOException e) {\n");
				changeFile.append("e.printStackTrace();\n");
				changeFile.append("}\n");
				//changeFile.append("}\n");
				changeFile.append("}\n");
				CtMethod m = CtNewMethod.make(changeFile.toString(), traceWriterClass);
				m.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.SYNCHRONIZED);
				traceWriterClass.addMethod(m);

				StringBuilder write = new StringBuilder();
				write.append("public static synchronized void write(java.lang.String line){\n");
				//write.append("String toWrite=line.substring(0, line.lastIndexOf('.'));\n");
				write.append("String toWrite=line;\n");
				write.append(
						"if(TestsTraces.fileName!=null && TestsTraces.out!=null && !TestsTraces.q.contains(toWrite)){\n");

				write.append("TestsTraces.out.println(\"[inst2] + \"+toWrite);\n");
				write.append("TestsTraces.out.flush();\n");
				write.append("TestsTraces.q.add(toWrite);\n");
				write.append("}\n");
				write.append("}\n");

				StringBuilder Add = new StringBuilder();
				Add.append("public static synchronized void Add(String line){\n");
				Add.append("if(line.equals(TestsTraces.testClass))\n");
				Add.append("return;\n");
				Add.append("TestsTraces.q.add(line);\n");
				Add.append("while(TestsTraces.q.size()>=10){\n");
				Add.append("TestsTraces.q.remove();\n");
				Add.append("}\n");
				Add.append("}\n");
				m = CtNewMethod.make(Add.toString(), traceWriterClass);
				m.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.SYNCHRONIZED);
				traceWriterClass.addMethod(m);

				m = CtNewMethod.make(write.toString(), traceWriterClass);
				m.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.SYNCHRONIZED);
				traceWriterClass.addMethod(m);

				traceWriterClass.writeFile("target\\classes");
				traceWriterClass.toClass(loader, protectionDomain);
				gcp.addGeneratedClass(traceWriterClass);
				traceWriterClass.writeFile("target\\classes\\org\\eclipse\\cdt");

			} catch (Exception e) {
				System.out.println("exception in initiate");

				e.printStackTrace();
			}

		}

	}

	public byte[] nicerTransform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

		this.initiate(className, loader, protectionDomain);

		byte[] byteCode = classfileBuffer;
		try {
			loader.loadClass("TestsTraces");
		} catch (Exception e) {
		}
		String[] s = className.split("/");
		boolean eclipse = s[0].equals("org") && s[1].equals("eclipse") && s[2].equals("cdt");
		boolean poi = s[0].equals("org") && s[1].equals("apache") && s[2].equals("poi");
		boolean ant = s[0].equals("org") && s[1].equals("apache") && s[2].equals("tools");
		boolean tomPack = s[2].equals("catalina") || s[2].equals("coyote") || s[2].equals("el") || s[2].equals("jasper")
				|| s[2].equals("juli") || s[2].equals("naming") || s[2].equals("tomcat");
		boolean tomcat = s[0].equals("javax") || s[0].equals("org") && s[1].equals("apache") && tomPack;
		boolean myapp = s[0].equals("com") && s[1].equals("mycompany") && s[2].equals("app");
		boolean surefire = s[3].equals("surefire");
		boolean lang = s[0].equals("java");
		boolean mvn = s[0].equals("org") && s[1].equals("apache") && s[2].equals("maven");
		boolean sun = s[0].equals("sun") || s[1].equals("sun");
		boolean junit = s[0].equals("org") && s[1].equals("junit");
		boolean osgi = s[0].equals("org") && s[1].equals("eclipse") && s[2].equals("osgi");
		//System.out.println(s[0] +" " +s[1]+" " +s[2]+" " +s[3]);
		ClassPool cp = ClassPool.getDefault();
		String name = className.replace("/", ".");
		name = name.split("$")[0];
		cp.importPackage(name);
		if (!surefire && !lang && !sun && !junit && !mvn && !osgi) {
			try {
				cp.importPackage(name);
				cp.appendClassPath(System.getProperty("user.dir"));
				CtClass cc = cp.get(name);
				CtMethod[] methods = cc.getDeclaredMethods();
				if (!cc.isInterface()) {
					for (CtMethod method : methods) {
						CtMethod m = cc.getDeclaredMethod(method.getName());
						if (m.isEmpty()) {
							continue;
						}
						String met = cc.getName() + "@" + method.getName();
						StringBuffer toInsert = new StringBuffer();
						StringBuffer toInsertAfter = new StringBuffer();

						// Load trace writer class dynamically
						String baseAfterCode = "" 
								+ "long tid = Thread.currentThread().getId();"
								+ "StringBuilder sbArgs = new StringBuilder();"
								+ "long tid = Thread.currentThread().getId();"
								+ "StringBuilder sbArgs = new StringBuilder();" 
								+ "{{TRACE_STATIC_INVOKING_OBJECT}}"
								+ "{{TRACE_ARGUMENTS}}" 
								+ "{{TRACE_EXCEPTION_OR_RETVAL}}"
								+ "Class testsRunnerClass=null;" 
								+ "try{"
								+ "		testsRunnerClass = Class.forName(\"TestsTraces\",true, \"amir\".getClass().getClassLoader().getSystemClassLoader());"
								+ "}catch(Exception exception){" 
								+ "		if(testsRunnerClass==null){"
								+ "			try{"
								+ "				URL classUrl = new URL(System.getProperty(\"user.dir\"));"
								+ "				URLClassLoader myLoader = URLClassLoader.newInstance(new URL[]{classUrl}, \"amir\".getClass().getClassLoader().getSystemClassLoader());"
								+ "				System.out.println(\"before load class \" + myLoader);"
								+ "				testsRunnerClass = Class.forName(\"TestsTraces\",true, myLoader);"
								+ "			}catch(Exception exception2){" 
								+ "			}" 
								+ "		}" 
								+ "}" 
								+ "try{"
								+ "		Class[] cArg = new Class[1];" 
								+ "		cArg[0] = String.class;"
								+ "		Object[] paramsForRecord = {\"" + met + "\" + sbArgs.toString()};"
								+ "		Object[] paramsForNewFile = {\"" + met + "\" };"
								+ "		{{TRIGGER_CHANGE_FILE_ADDITIONAL}}"
								+ "		testsRunnerClass.getMethod(\"write\", cArg).invoke(null, paramsForRecord);"
								+ "} catch (Exception e) {" 
								+ "		System.out.println(\"exception e\");"
								+ "		e.printStackTrace();" 
								+ "}";

						//add invoking object's hashcode if not a static method
						String staticAdditionalString = "";
						if (!Modifier.isStatic(m.getModifiers())) {
							staticAdditionalString = "sbArgs.append(\",\" + System.identityHashCode( $0 ) );";
						}
						baseAfterCode = baseAfterCode.replace("{{TRACE_STATIC_INVOKING_OBJECT}}", staticAdditionalString);

						//add arguments (hashcode for non-primitives)
						CtClass[] pTypes = m.getParameterTypes();
						StringBuffer argumentsBuffer = new StringBuffer();
						for (int i = 0; i < pTypes.length; ++i) {
							CtClass pType = pTypes[i];
							if (pType.isPrimitive()) {
								argumentsBuffer.append("sbArgs.append( \",\" + $args[" + i + "] );");
							} else {
								argumentsBuffer.append(
										"sbArgs.append( \",\" + System.identityHashCode( $args[" + i + "] ) );");
							}
						}
						baseAfterCode = baseAfterCode.replace("{{TRACE_ARGUMENTS}}", argumentsBuffer.toString());

						//if this is a test function, create a new file
						String testFunctionAdditionalCode = "";
						boolean isTestFunction = (cc.getName().contains("Test") && method.getName().startsWith("test"));
						if (isTestFunction) {
							testFunctionAdditionalCode = "testsRunnerClass.getMethod(\"changefile\", cArg).invoke(null, paramsForNewFile);";
						}
						baseAfterCode = baseAfterCode.replace("{{TRIGGER_CHANGE_FILE_ADDITIONAL}}", testFunctionAdditionalCode);

						//create retval after-code (for when method is succesfull)
						String retvalAdditionStr;
						try {
							CtClass retType = m.getReturnType();
							if (retType.isPrimitive()) {
								retvalAdditionStr = "sbArgs.append( \",\" + $_ );";
							} else {
								retvalAdditionStr = "sbArgs.append( \",\" + System.identityHashCode( $_ ) );";
							}
						} catch (javassist.NotFoundException e) {
							// void retval, do nothing
							retvalAdditionStr = "";
						}
						String retvalAfterCode = baseAfterCode.replace("{{TRACE_EXCEPTION_OR_RETVAL}}", retvalAdditionStr);

						//create error after-code (for when method is errornous)
						// CtClass etype = ClassPool.getDefault().get("java.lang.RuntimeException");
						// String errorAdditionStr = "sbArgs.append( \", __$$EXCEPTION$$__\" );";
						// String errorAfterCode = baseAfterCode.replace("{{TRACE_EXCEPTION_OR_RETVAL}}", errorAdditionStr);

						//add dynamic code
						try {
							int mod = m.getModifiers();
							boolean editableBody = !Modifier.isNative(mod) && !Modifier.isAbstract(mod)
									&& !Modifier.isFinal(mod);
							if (editableBody) {
								m.insertAfter(retvalAfterCode, false); //false means it is NOT "asFinally" -> executes only if an exception was not thrown
								// m.addCatch("{ " + errorAfterCode  + " throw $e; }", etype);
							}
						} catch (Exception e) {
							System.out.println("exception in insertBefore " + met);
							System.out.println(e.getMessage());
							System.err.println(met);
							e.printStackTrace();
						}
					}
				}
				byteCode = cc.toBytecode();
				cc.detach();
			} catch (Exception ex) {
				System.out.println("exception in nicer");
				ex.printStackTrace();
				System.err.println(ex.getMessage());
			}
		}

		return byteCode;
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

		return nicerTransform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
	}
}