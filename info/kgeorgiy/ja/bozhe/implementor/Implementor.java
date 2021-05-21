package info.kgeorgiy.ja.bozhe.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.security.CodeSource;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;


/**
 * Implementation of {@link Impler} which allows generate {@code .java} code for given parent class.
 *
 * @author Ilona Bozhe
 */
public class Implementor implements JarImpler {
    private final Manifest manifest;

    /**
     * Empty default constructor, creates default manifest.
     */
    public Implementor() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "Ilona Bozhe");
        this.manifest = manifest;
    }

    /**
     * Creates directories for the given path.
     *
     * @param filePath path which directories needs to be created.
     * @throws ImplerException if directories can't be crated
     */
    protected void createDirectories(Path filePath) throws ImplerException {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (FileAlreadyExistsException e) {
            throw new ImplerException("Invalid path: the directory name exists but is not a directory.", e);
        } catch (IOException e) {
            // :NOTE: no harm in not stopping there
//            throw new ImplerException("Invalid path: the directory can't be created.", e);
        }
    }

    /**
     * Creates a class from input {@code String className}.
     *
     * @param className of the class
     * @return the class from the given className
     * @throws ImplerException if the className is not correct
     */
    protected static Class<?> createClass(String className) throws ImplerException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ImplerException("Invalid class name: " + className, e);
        }
    }


    /**
     * Generates an implementation of the given parent class in the given root folder.
     *
     * @param token of the given parent class
     * @param root  folder where an implementation should be created
     * @throws ImplerException if there were any troubles with generating the code
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkArgs(token, root);
        Path outputFilePath = getPathFromRoot(token, root, ".java");
        createDirectories(outputFilePath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath)) {
            addPackage(token, writer);
            startClass(token, writer);
            addConstructors(token, writer);
            addMethods(token, writer);
            writer.write(END);
        } catch (IOException e) {
            throw new ImplerException("Invalid output file.", e);
        }
    }

    /**
     * Generates a package from the given class {@code token}.
     *
     * @param token  the given parent class
     * @param writer writes the generated implementations to the output file
     * @throws IOException if the writer didn't work correctly
     */
    private static void addPackage(Class<?> token, BufferedWriter writer) throws IOException {
        writeCode(writer, token.getPackageName().isEmpty() ? "" : "package " + token.getPackageName() + EOL);
    }


    /**
     * Generates default start implementations for the given class {@code token}.
     *
     * @param token  the given parent class
     * @param writer writes the generated implementations to the output file
     * @throws IOException if the writer didn't work correctly
     */
    private static void startClass(Class<?> token, BufferedWriter writer) throws IOException {
        String parentName = token.getCanonicalName();
        writeCode(writer, "public class" + WS + getClassName(token) + WS +
                (token.isInterface() ? "implements" : "extends") + WS + parentName + BEGIN);
    }


    /**
     * Generates a list of exceptions fot the given method or constructor
     *
     * @param executable represents method or constructor
     * @return string version of the generated list of exceptions
     */
    private static String getExceptions(Executable executable) {
        StringBuilder exceptions = new StringBuilder();
        for (Class<?> exception : executable.getExceptionTypes()) {
            exceptions.append(exception.getCanonicalName()).append(COMMA);
        }
        if (exceptions.length() > 0) {
            exceptions.delete(exceptions.length() - 2, exceptions.length());
            return "throws" + WS + exceptions.toString();
        } else {
            return "";
        }
    }

    /**
     * Generates default implementation for the given constructor or method.
     *
     * @param executable  represents method or constructor
     * @param declaration the type of the constructor or the name and the type if the method
     * @param body        the constructor or the method
     * @param writer      writes the generated implementations to the output file
     * @throws IOException if the writer didn't work correctly
     */
    private static void addExecutable(Executable executable,
                                      Function<Executable, String> declaration,
                                      Function<Executable, String> body,
                                      BufferedWriter writer) throws IOException {
        String mods = Modifier.toString(executable.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT);
        writeCode(writer, mods + WS + declaration.apply(executable) + OPEN_PAR);
        int parNum = 1;
        for (Class<?> type : executable.getParameterTypes()) {
            writeCode(writer, type.getCanonicalName() + WS + "p" + parNum++ +
                    (parNum != executable.getParameterTypes().length + 1 ? COMMA : ""));
        }
        // :NOTE: please, understand why you do this
        writeCode(writer, CLOSE_PAR + getExceptions(executable) + BEGIN + body.apply(executable) + EOL + END);
    }

    /**
     * Generates a body for the given constructor.
     *
     * @param constructor which body needs to be implemented
     * @return string version of the generated implementation
     */
    private static String constructorBody(Executable constructor) {
        StringBuilder body = new StringBuilder().append("super").append(OPEN_PAR);
        for (int i = 1; i <= constructor.getParameterCount(); ++i) {
            body.append(WS + "p").append(i).append(i != constructor.getParameterCount() ? COMMA : "");
        }
        return body.append(CLOSE_PAR).toString();
    }

    /**
     * Generates default constructor implementations for the given class {@code token}.
     *
     * @param token  the given parent class
     * @param writer writes the generated implementations to the output file
     * @throws IOException     if the writer didn't work correctly
     * @throws ImplerException if constructors can't be implemented
     */
    private static void addConstructors(Class<?> token, BufferedWriter writer) throws IOException, ImplerException {
        boolean hasDefault = false, hasNonPrivate = false;
        if (!token.isInterface()) {
            for (Constructor<?> constructor : token.getDeclaredConstructors()) {
                hasDefault = hasDefault || (constructor.getParameterTypes().length == 0);
                if (!Modifier.isPrivate(constructor.getModifiers())) {
                    hasNonPrivate = true;
                    addExecutable(constructor, (x) -> getClassName(token), Implementor::constructorBody, writer);
                }
            }
        }

        if (hasDefault && !hasNonPrivate) {
            throw new ImplerException("Invalid class: All possible constructors are private.");
        }
    }

    /**
     * Gets all the abstract of final methods from the given list and collects their wrappings to the HashSet.
     *
     * @param methods which needs to be filtered and wrapped.
     * @return a list of the collected HashedMethod methods.
     */
    private static List<HashedMethod> processMethods(Method[] methods) {
        return Arrays.stream(methods)
                .filter(method -> Modifier.isFinal(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()))
                .map(HashedMethod::new)
                .collect(Collectors.toList());
    }

    /**
     * Generates default methods implementations for the given class {@code token}.
     *
     * @param token  the given parent class
     * @param writer writes the generated implementations to the output file
     * @throws IOException if the writer didn't work correctly
     */

    private static void addMethods(Class<?> token, BufferedWriter writer) throws IOException {
        HashSet<HashedMethod> methods = new HashSet<>(processMethods(token.getMethods()));
        for (; token != null; token = token.getSuperclass()) {
            methods.addAll(processMethods(token.getDeclaredMethods()));
        }

        for (HashedMethod hashedMethod : methods) {
            if (!Modifier.isFinal(hashedMethod.getMethod().getModifiers())) {
                addExecutable(hashedMethod.getMethod(),
                        (mth) -> ((Method) mth).getReturnType().getCanonicalName() + WS + ((Method) mth).getName(),
                        (mth) -> "return" + getDefaultValue(((Method) mth).getReturnType()),
                        writer);
            }
        }
    }

    /**
     * Class to create a hashable version of {@link java.lang.reflect.Method}
     */
    private static class HashedMethod {
        Method method;

        /**
         * {@link HashedMethod} constructor
         *
         * @param method the given {@link Method}
         */
        HashedMethod(Method method) {
            this.method = method;
        }

        /**
         * @return the inner {@code method}
         */
        Method getMethod() {
            return method;
        }


        /**
         * Indicates if the other object is equal to the current one.
         *
         * @param o the other objects which needs to be compared
         * @return {@code true} if the current and the other object are same and {@code false} in the other case
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HashedMethod that = (HashedMethod) o;
            return Arrays.equals(method.getParameterTypes(), that.method.getParameterTypes())
                    && Objects.equals(method.getReturnType(), that.method.getReturnType())
                    && Objects.equals(method.getName(), that.method.getName());
        }

        /**
         * @return a hash code for this object.
         */
        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(method.getParameterTypes()), method.getReturnType(), method.getName());
        }
    }

    /**
     * Makes available to run JarImplementor from console.
     *
     * @param args contains two args: token and root if there shouldn't be jar archive created or three args:
     *             -jar flag, token and root if jar archive should be crated.
     */
    public static void main(String[] args) {
        if (args == null || args.length < 2 || args.length > 3 || (args.length == 2 && !args[0].equals("-jar"))) {
            System.err.println("Invalid number of args: set -jar flag [optional], class token and root path.");
        } else {
            try {
                if (args.length == 2) {
                    new Implementor().implement(createClass(args[0]), createPath(args[1]));
                } else {
                    new Implementor().implementJar(createClass(args[1]), createPath(args[2]));
                }
            } catch (ImplerException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Generates a jar archive of the implementation of the given parent class in the given root folder.
     *
     * @param token of the given parent class
     * @param root  folder where a jar archive should be created
     * @throws ImplerException if there were any troubles with generating the code
     */
    @Override
    public void implementJar(Class<?> token, Path root) throws ImplerException {
        checkArgs(token, root);
        Path tempPath = createTempOutputPath(root);
        try {
            implement(token, tempPath);
            compile(token, tempPath);
            createJar(token, root, tempPath);
        } finally {
            deleteTempOutputPath(tempPath);
        }
    }

    /**
     * Creates temp directory to keep implementation files
     *
     * @param root folder where a jar archive should be created
     * @return temp directory path
     * @throws ImplerException if path wasn't created
     */
    private Path createTempOutputPath(Path root) throws ImplerException {
        createDirectories(root);
        try {
            return Files.createTempDirectory(root.toAbsolutePath().getParent(), "tempImpl");
        } catch (IOException e) {
            throw new ImplerException("Can't create temp directory.", e);
        }
    }

    /**
     * Deletes temp directory when the jat archive was created
     *
     * @param path of the current directory
     * @return a flag whether the directory was deleted successfully
     */
    private boolean deleteTempOutputPath(Path path) {
        File file = path.toFile();
        File[] children = file.listFiles();
        if (children == null) {
            return file.delete();
        }
        return Arrays.stream(children).anyMatch(child -> !deleteTempOutputPath(child.toPath())) && file.delete();
    }

    /**
     * Compiles code of the created implementation class.
     *
     * @param token of the given parent class
     * @param dir   directory where implementation is created
     * @throws ImplerException if code wasn't compiled
     */
    private void compile(Class<?> token, Path dir) throws ImplerException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Compilation error: Can't find java compiler.");
        }
        try {
            CodeSource classPathSource = token.getProtectionDomain().getCodeSource();
            int returnCode;
            if (classPathSource != null) {
                returnCode = compiler.run(null, null, null, "-cp",
                        Paths.get(classPathSource.getLocation().toURI()).toString(),
                        getPathFromRoot(token, dir, JAVA).toString());
            } else {
                returnCode = compiler.run(null, null, null,
                        getPathFromRoot(token, dir, JAVA).toString());
            }
            if (returnCode != 0) {
                throw new ImplerException("Compilation error: code can't be compiled. Status code: " + returnCode);
            }
        } catch (URISyntaxException e) {
            throw new ImplerException("Compilation error: Can't fetch additional resources.", e);
        }
    }

    /**
     * Creates jar archive.
     *
     * @param token of the given parent class
     * @param root  folder where a jar archive should be created
     * @param temp  temp folder where implementation class was created
     * @throws ImplerException if jar file wasn't created
     */
    private void createJar(Class<?> token, Path root, Path temp) throws ImplerException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(root), manifest)) {
            jar.putNextEntry(new ZipEntry(getPath(token, CLASS, '/')));
            Files.copy(Paths.get(getPathFromRoot(token, temp, CLASS).toString()), jar);
        } catch (IOException e) {
            throw new ImplerException("Can't create JAR.", e);
        }
    }

    /* UTILS */

    /**
     * Check if arguments given for implementor are correct
     *
     * @param token the given parent class
     * @param root  folder where an implementation should be created
     * @throws ImplerException if arguments are incorrect
     */
    protected void checkArgs(Class<?> token, Path root) throws ImplerException {
        if (token == null || root == null) {
            throw new ImplerException("Invalid args: class and root path shouldn't be null.");
        }
        if (token.isPrimitive() || token.isArray() || Modifier.isPrivate(token.getModifiers())
                || Modifier.isFinal(token.getModifiers()) || token == Enum.class) {
            throw new ImplerException("Invalid class: this class can't be implemented.");
        }
    }

    /**
     * Creates a path from input {@code String pathName}.
     *
     * @param pathName of the necessary path
     * @return the path from the given pathName
     * @throws ImplerException if the pathName is not correct
     */
    protected static Path createPath(String pathName) throws ImplerException {
        try {
            return Paths.get(pathName);
        } catch (InvalidPathException e) {
            throw new ImplerException("Invalid path: " + pathName, e);
        }
    }

    /**
     * Generates a class name for the generated implementation class.
     *
     * @param token the given parent class
     * @return the name of the new class
     */
    protected static String getClassName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Determines new file path
     *
     * @param token     the given parent class
     * @param end       file extension
     * @param separator path separator
     * @return determined path
     */
    protected String getPath(Class<?> token, String end, char separator) {
        return token.getPackageName().replace('.', separator) + separator + getClassName(token) + end;
    }

    /**
     * Determines new file path starting with the given root
     *
     * @param token the given parent class
     * @param root  folder where an implementation should be created
     * @param end   file extension
     * @return determined path
     */
    protected Path getPathFromRoot(Class<?> token, Path root, String end) {
        return root.resolve(getPath(token, end, File.separatorChar));
    }

    protected static void writeCode(BufferedWriter writer, String code) throws IOException {
        for (char c : code.toCharArray()) {
            writer.write(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        }
    }

    /**
     * Identifies a correct default value for the given type.
     *
     * @param token the given class
     * @return the default value for the given class
     */
    private static String getDefaultValue(Class<?> token) {
        if (token.equals(void.class)) {
            return "";
        } else if (token.equals(boolean.class)) {
            return WS + "false";
        } else if (token.isPrimitive()) {
            return WS + "0";
        } else {
            return WS + "null";
        }
    }

    /* CONSTANTS */

    static final String LINE_SEP = System.lineSeparator();
    static final String EOL = ";" + LINE_SEP;
    static final String BEGIN = " {" + LINE_SEP;
    static final String END = "}" + LINE_SEP;
    static final String OPEN_PAR = "(";
    static final String CLOSE_PAR = ") ";
    static final String COMMA = ", ";
    static final String WS = " ";
    static final String CLASS = ".class";
    static final String JAVA = ".java";
}
// java -cp . -p . -m info.kgeorgiy.java.advanced.implementor jar-advanced info.kgeorgiy.ja.bozhe.implementor.JarImplementor