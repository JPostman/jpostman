package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Converts cached JSON scalar nodes into ordinary Java values. */
public final class JPostmanCacheValueConverter {

    private JPostmanCacheValueConverter() {
    }

    /**
     * Unwraps JSON null and primitive objects without introducing a hard Gson
     * dependency into the annotations module. Objects and arrays are returned
     * unchanged.
     */
	public static Object unwrap(Object value) {
        if (value == null) {
            return null;
        }
        Object secretValue = revealSecretValue(value);
        if (secretValue != value) {
            return unwrap(secretValue);
        }
        String className = value.getClass().getName();
        if ("com.google.gson.JsonNull".equals(className)) {
            return null;
        }
        if (!"com.google.gson.JsonPrimitive".equals(className)) {
            return value;
        }
        if (booleanMethod(value, "isString")) {
            return invoke(value, "getAsString");
        }
        if (booleanMethod(value, "isBoolean")) {
            return invoke(value, "getAsBoolean");
        }
        if (booleanMethod(value, "isNumber")) {
            return invoke(value, "getAsNumber");
        }
        return value;
    }

    private static Object revealSecretValue(Object value) {
        if (!isSecretWrapper(value)) {
            return value;
        }

        Object revealed = JPostmanInfo.reveal(value);
        if (revealed != value) {
            return revealed;
        }

        for (String methodName : new String[] { "reveal", "getValue", "value", "get", "rawValue" }) {
            Object result = invokeAccessor(value, methodName);
            if (result != NO_VALUE && result != value) {
                return result;
            }
        }

        for (String fieldName : new String[] { "value", "secret", "rawValue" }) {
            Object result = readField(value, fieldName);
            if (result != NO_VALUE && result != value) {
                return result;
            }
        }

        return value;
    }

    private static final Object NO_VALUE = new Object();

    private static boolean isSecretWrapper(Object value) {
        if (value == null) {
            return false;
        }
        if (JPostmanInfo.isSecretValue(value)) {
            return true;
        }
        String simpleName = value.getClass().getSimpleName();
        return simpleName.endsWith("ServerValue")
                || simpleName.endsWith("SecretValue")
                || simpleName.endsWith("SecureValue");
    }

    private static Object invokeAccessor(Object target, String methodName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
                    return NO_VALUE;
                }
                if (!method.canAccess(target)) {
                    method.setAccessible(true);
                }
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
                throw new IllegalArgumentException("Unable to reveal cached secret value.", e);
            }
        }
        return NO_VALUE;
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (!field.canAccess(target)) {
                    field.setAccessible(true);
                }
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalArgumentException("Unable to reveal cached secret value.", e);
            }
        }
        return NO_VALUE;
    }

    public static <T> T convert(Object value, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Cache conversion type must not be null.");
        }
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return null;
        }
        Class<?> boxed = boxed(type);
        if (boxed.isInstance(unwrapped)) {
            @SuppressWarnings("unchecked")
            T result = (T) unwrapped;
            return result;
        }
        if (boxed == String.class) {
            return cast(type, String.valueOf(unwrapped));
        }
        if (unwrapped instanceof Number) {
            Number number = (Number) unwrapped;
            if (boxed == Integer.class) return cast(type, number.intValue());
            if (boxed == Long.class) return cast(type, number.longValue());
            if (boxed == Double.class) return cast(type, number.doubleValue());
            if (boxed == Float.class) return cast(type, number.floatValue());
            if (boxed == Short.class) return cast(type, number.shortValue());
            if (boxed == Byte.class) return cast(type, number.byteValue());
        }
        if (boxed == Boolean.class && unwrapped instanceof CharSequence) {
            return cast(type, Boolean.valueOf(unwrapped.toString()));
        }
        if (boxed == Character.class && unwrapped instanceof CharSequence
                && unwrapped.toString().length() == 1) {
            return cast(type, unwrapped.toString().charAt(0));
        }
        if (boxed.isEnum() && unwrapped instanceof CharSequence) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object enumValue = Enum.valueOf((Class<? extends Enum>) boxed, unwrapped.toString());
            return cast(type, enumValue);
        }
        throw new IllegalArgumentException("Cannot convert cached value of type "
                + unwrapped.getClass().getName() + " to " + type.getName() + ".");
    }

    private static boolean booleanMethod(Object target, String method) {
        Object value = invoke(target, method);
        return Boolean.TRUE.equals(value);
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to read cached JSON value.", e);
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Class<T> type, Object value) {
        return (T) value;
    }
}
