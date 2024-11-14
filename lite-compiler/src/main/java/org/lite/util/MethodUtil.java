package org.lite.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author qingbozhang
 * @since 2024/11/14 14:39
 */
public class MethodUtil {

    public static MethodResult getMethodSignature(Method method) {
        // 获取方法的修饰符（例如 public, private）
        String modifiers = Modifier.toString(method.getModifiers());

        // 获取返回类型
        String returnType = getGenericTypeName(method.getGenericReturnType());

        // 获取方法名
        String methodName = method.getName();

        // 获取参数列表并格式化为字符串
        String parameters = Arrays.stream(method.getGenericParameterTypes())
                .map(MethodUtil::getGenericTypeName)
                .collect(Collectors.joining(", "));

        // 组装成完整的签名字符串
        String fullName = String.format("%s %s %s(%s)", modifiers, returnType, methodName, parameters);
        MethodResult result = new MethodResult();
        result.setSignatureFullName(fullName);
        // 不考虑全限定名 只看方法名，忽略重载
        result.setMethodName(methodName);

        return result;
    }

    // 递归获取泛型类型的完整类型名称
    private static String getGenericTypeName(Type type) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).getName();  // 如果是原始类型，直接返回
        } else if (type instanceof ParameterizedType paramType) {
            // 获取泛型类型的原始类型（例如 List）
            String rawType = ((Class<?>) paramType.getRawType()).getName();
            // 获取泛型参数的类型（例如 String）
            String genericType = Arrays.stream(paramType.getActualTypeArguments())
                    .map(MethodUtil::getGenericTypeName)
                    .collect(Collectors.joining(", "));
            // 返回如 List<java.lang.String> 的格式
            return rawType + "<" + genericType + ">";
        } else {
            return type.toString();  // 如果其他类型，直接返回
        }
    }

    private MethodUtil() {
    }
}
