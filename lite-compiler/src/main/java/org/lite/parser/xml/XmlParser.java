package org.lite.parser.xml;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.lite.util.MethodResult;
import org.lite.util.MethodUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author qingbozhang
 * @since 2024/11/13 20:50
 */
@Slf4j
public class XmlParser {
    private static final SAXReader READER = new SAXReader();

    static {
        READER.setEncoding("UTF-8");
    }


    public static void parseFile(Class<?> klass, File file) throws DocumentException {
        Document document = READER.read(file);
        Method[] methods = klass.getMethods();

        List<Element> elements = document.getRootElement().elements();
        Map<String, MethodResult> methodResultMap = new HashMap<>();

        for (Method method : methods) {
            MethodResult result = MethodUtil.getMethodSignature(method);
            methodResultMap.put(result.getMethodName(), result);
        }
        for (Element element : elements) {
            String name = element.getName().toLowerCase();
            if (!"select".equals(name)) {
                continue;
            }
        }
    }
}
