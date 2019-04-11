package veroy.research.et2.javassist;

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ByteArrayClassPath;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

public class MethodInstrumenter {

    private static AtomicInteger nextMethodId = new AtomicInteger(1);
    private static ConcurrentHashMap<String, Integer> methodIdMap = new ConcurrentHashMap();

    private static AtomicBoolean mainInstrumentedFlag = new AtomicBoolean(false);
    private InputStream instream;
    private String newName;
    private PrintWriter mwriter;

    public MethodInstrumenter(InputStream instream, String newName, PrintWriter methodsWriter) {
        this.instream = instream;
        this.newName = newName;
        this.mwriter = methodsWriter;

        ClassPool cp = ClassPool.getDefault();
        cp.importPackage("org.apache.log4j");
    }

    protected int getMethodId(String className, String methodName) {
        String key = className + "#" + methodName;
        methodIdMap.computeIfAbsent(key, k -> nextMethodId.getAndAdd(1));
        Integer result = methodIdMap.get(key);
        return ((result != null) ? (Integer) result : -1);
    }

    public CtClass instrumentMethods(ClassLoader loader) throws CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new LoaderClassPath(loader));
        CtClass ctKlazz = null;
        try {
            ctKlazz = cp.makeClass(instream);
        } catch (IOException exc) {
            System.err.println("IO error getting class/method:");
            exc.printStackTrace();
            System.exit(1);
        }

        String className = ctKlazz.getName();

        // Methods:
        // CtMethod[] methods = ctKlazz.getMethods();
        CtBehavior[] methods = ctKlazz.getDeclaredBehaviors();
        for (int ind = 0 ; ind < methods.length; ind++) {
            final CtBehavior method = methods[ind];
            final String methodName = method.getName();
            int modifiers = method.getModifiers();
            int methodId = getMethodId(className, methodName);

            if (shouldIgnore(modifiers, methodName)) {
                continue;
            }
            // DEBUG: System.err.println("XXX: " + className + " # " + methodName);
            if (method instanceof CtMethod) {
                method.instrument(
                        new ExprEditor() {
                            public void edit(NewExpr expr) throws CannotCompileException {
                                expr.replace("{ $_ = $proceed($$); veroy.research.et2.javassist.ETProxy.onObjectAlloc($_, 12345, 5678); }");
                            }
                        }
                );
                // Insert ENTRY and EXIT events:
                try {
                    if (Modifier.isStatic(modifiers)) {
                        // TODO: if (mainInstrumentedFlag.get() || !methodName.equals("main")) {
                        method.insertBefore("{ veroy.research.et2.javassist.ETProxy.onEntry(" + methodId + ", (Object) null); }");
                        // TODO: } else {
                        // TODO:     // main function:
                        // TODO:     System.err.println("XXX: MAIN => " + className + " # " + methodName);
                        // TODO:     method.insertBefore("{  }");
                        // TODO:     mainInstrumentedFlag.set(true);
                        // TODO: }
                        // TODO: if (methodName.indexOf("main") 
                    } else {
                        method.insertBefore("{ veroy.research.et2.javassist.ETProxy.onEntry(" + methodId + ", (Object) this); }");
                    }
                } catch (CannotCompileException exc) {
                    System.err.println("Error compiling 'insertBefore' code into class/method: " + methodName + " ==? " + methodName.equals("equals"));
                    exc.printStackTrace();
                    throw exc;
                }
                try {
                    method.insertAfter("{ veroy.research.et2.javassist.ETProxy.onExit(" + methodId + "); }");
                } catch (CannotCompileException exc) {
                    System.err.println("Error compiling 'insertAfter' code into class/method: " + methodName);
                    exc.printStackTrace();
                    throw exc;
                }
            } // TODO: else if (method instanceof CtConstructor) {
                // TODO:     try {
                // TODO:         // (Object allocdObject, int allocdClassID, int allocSiteID)
                // TODO:         method.insertAfter("{ veroy.research.et2.javassist.ETProxy.onObjectAlloc($_, 12345, 5678); }");
                // TODO:         // TODO: method.replace("{  $_ = $proceed($$); }");
                // TODO:     } catch (CannotCompileException exc) {
                // TODO:         System.err.println("Error compiling 'insertBefore' code into constructor: " + methodName);
                // TODO:         exc.printStackTrace();
                // TODO:         throw exc;
                // TODO:     }

                // TODO:
            // TODO: }
        }
        ctKlazz.setName(newName);

        return ctKlazz;
    }

    protected boolean shouldIgnore(int modifiers, String methodName) {
        return (Modifier.isNative(modifiers) ||
                Modifier.isAbstract(modifiers) ||
                methodName.equals("equals") ||
                methodName.equals("finalize") ||
                methodName.equals("toString") ||
                methodName.equals("wait"));
    }
}