import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

/**
 * Generate assertions from JSON
 * @author shogo.kataoka
 */
public class JsonTestCodeGen {
    /**
     * Entry point to the application
     * @param args arguments from command line
     */
    public static void main(String[] args) {
        final JsonTestCodeGen generator = new JsonTestCodeGen();

        final String filepath = args.length > 0 ? args[0] : "test.json";
        final File file = new File(filepath);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            final String jsonString = reader.lines().collect(Collectors.joining());
            final File outfile = new File(filepath + "_test.txt");
            if (!outfile.exists()) {
                outfile.createNewFile();
            }
            final List<LinkedTreeMap<String, Object>> jsonObject = JsonObjectFactory.fromJsonString(jsonString);
            System.out.println(jsonObject);
            generator.write(outfile, generator.generateTestCode(jsonObject, jsonString.startsWith("[")));
        } catch (final IOException e) {
            throw new RuntimeException("failed to generate test code" + file.getPath(), e);
        }
    }

    /**
     * Write to file
     * @param file outputFile (NotNull)
     * @param code generated code (NotNull)
     */
    public void write(File file, String code) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")))) {
            writer.write(code);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to writer file : " + file.getPath(), e);
        }
    }

    /**
     * Generate test code from JSON
     * @param jsonObjects JSON object (NotNull)
     * @param isList {@code true} : JSON data is list
     * @return Returns assertions (NotNull)
     * @throws IOException
     */
    public String generateTestCode(List<LinkedTreeMap<String, Object>> jsonObjects, boolean isList) throws IOException {
        return doGenerate(jsonObjects, isList);
    }

    private String doGenerate(List<LinkedTreeMap<String, Object>> jsonObjects, boolean isList) {
        final StringBuilder sb = new StringBuilder();
        processListTop(jsonObjects, isList, sb);
        return sb.toString();
    }

    private void processList(List<LinkedTreeMap<String, Object>> jsonObjects, String parent, int level, StringBuilder sb) {
        for (int i = 0; i < jsonObjects.size(); ++i) {
            final Object obj = jsonObjects.get(i);
            if (Map.class.isAssignableFrom(obj.getClass())) {
                @SuppressWarnings("unchecked")
                final LinkedTreeMap<String, Object> jsonObj = (LinkedTreeMap<String, Object>) obj;
                final String parentName = parent.isEmpty() ? "" : parent + ".get(" + i + ")";
                processMap(jsonObj, parentName, level + 1, sb);
            } else {
                final String parentName = parent.isEmpty() ? "" : parent + ".get(" + i + ")";
                final String ret = "assertEquals(" + obj + ", " + parentName + ");";
                log(ret, 0);
                sb.append(ret);
                sb.append(System.lineSeparator());
            }
        }
    }

    private void processListTop(List<LinkedTreeMap<String, Object>> jsonObjects, boolean isList, StringBuilder sb) {
        for (int i = 0; i < jsonObjects.size(); ++i) {
            final Object obj = jsonObjects.get(i);
            if (Map.class.isAssignableFrom(obj.getClass())) {
                @SuppressWarnings("unchecked")
                final LinkedTreeMap<String, Object> jsonObj = (LinkedTreeMap<String, Object>) obj;
                final String parentName = "result" + (isList ? ".get(" + i + ")" : "");
                processMap(jsonObj, parentName, 1, sb);
            } else {
                final String parentName = "result" + (isList ? ".get(" + i + ")" : "");
                final String ret = "assertEquals(" + obj + ", " + parentName + ");";
                log(ret, 0);
                sb.append(ret);
                sb.append(System.lineSeparator());
            }
        }
    }

    private void processMap(Map<String, Object> jsonObject, String parent, int level, StringBuilder sb) {
        jsonObject.forEach((k, v) -> {
            processPair(k, v, parent, level + 1, sb);
        });
    }

    @SuppressWarnings("unchecked")
    private void processPair(String key, Object value, String parent, int level, StringBuilder sb) {
        final String parentName = parent.isEmpty() ? "" : parent + ".";
        if (value != null) {
            final Class<? extends Object> clazz = value.getClass();
            if (Map.class.isAssignableFrom(clazz)) {
                processMap((LinkedTreeMap<String, Object>) value, parentName + key, level + 1, sb);
            } else if (List.class.isAssignableFrom(clazz)) {
                processList((List<LinkedTreeMap<String, Object>>) value, parentName + key, level + 1, sb);
            } else {
                final String ret = AssertGenUtil.buildAssertMethodCall(key, value, parentName);
                log(ret, 0);
                sb.append(ret);
                sb.append(System.lineSeparator());
            }
        } else {
            final String ret = AssertGenUtil.buildAssertMethodCall(key, "null", parentName);
            log(ret, 0);
            sb.append(ret);
        }
    }

    private String getIndent(int level) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private void log(String msg, int level) {
        System.out.println(getIndent(level) + msg);
    }
}

/**
 *
 * @author shogo.kataoka
 */
class JsonObjectFactory {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(JsonObjectFactory.class);

    /**
     * Parse JSON from string
     * @param jsonString JSON string (NotNull)
     * @return Returns JSON object list (NotNull)
     */
    public static List<LinkedTreeMap<String, Object>> fromJsonString(String jsonString) {
        return jsonString.startsWith("[") ? readJsonObjectList(jsonString) : readJsonObject(jsonString);
    }

    /**
     * parse JSON array from string
     * @param jsonString JSON string (NotNull)
     * @return Returns JSON object list (NotNull)
     */
    public static List<LinkedTreeMap<String, Object>> readJsonObjectList(String jsonString) {
        logger.debug("==============================================");
        logger.debug("   FROM JSON ( LIST )");
        logger.debug("==============================================");
        final Gson gson = new GsonBuilder().serializeNulls().create();
        //@formatter:off
        return gson.fromJson(jsonString, new TypeToken<List<LinkedTreeMap<String,Object>>>() {}.getType());
        //@formatter:on
    }

    /**
     * parse JSON object from string
     * @param jsonString (NotNull)
     * @return Returns JSON object list (NotNull)
     */
    public static List<LinkedTreeMap<String, Object>> readJsonObject(String jsonString) {
        logger.debug("==============================================");
        logger.debug("   FROM JSON");
        logger.debug("==============================================");
        final Gson gson = new GsonBuilder().serializeNulls().create();
        final List<LinkedTreeMap<String, Object>> list = new LinkedList<>();
        //@formatter:off
        final Type type = new TypeToken<LinkedTreeMap<String, Object>>() {}.getType();
        //@formatter:on
        list.add(gson.fromJson(jsonString, type));
        return list;
    }
}

/**
 *
 * @author shogo.kataoka
 */
class AssertGenUtil {
    /**
     * build assert method call statement
     * @param key JSON key (NotNull)
     * @param value JSON value (NotNull)
     * @param parent parent object (NullAllowed)
     * @return Returns string of assert method statement (NotNull)
     */
    public static String buildAssertMethodCall(String key, Object value, String parent) {
        if (value.toString().equals("null")) {
            return String.format("assertNull(%s%s);", parent, key);
        }
        final Class<?> valueClass = value.getClass();
        final String valString = buildValueString(value, valueClass);

        if (Boolean.class.isAssignableFrom(valueClass)) {
            return "assert" + ((Boolean) value ? "True" : "False") + String.format("(%s%s);", parent, key);
        } else {
            return String.format("assertEquals(%s, %s%s);", valString, parent, key);
        }
    }

    /**
     * build JSON value string
     * @param value JSON value (NotNull)
     * @param valueClass value's class (NotNull)
     * @return Returns values of java literal e.g "test" or 123
     */
    public static String buildValueString(Object value, Class<?> valueClass) {
        if (String.class.isAssignableFrom(valueClass)) {
            final String str = value.toString();
            return str.matches("[0-9]+") ? str : String.format("\"%s\"", value);
        } else if (Double.class.isAssignableFrom(valueClass)) {
            return "" + ((Double) value).intValue();
        }
        return value.toString();
    }
}