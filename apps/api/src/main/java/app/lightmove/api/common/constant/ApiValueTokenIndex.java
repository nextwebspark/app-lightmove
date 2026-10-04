package app.lightmove.api.common.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Each {@link ApiValueEnum}'s token → constant map, built once per enum on first lookup. */
final class ApiValueTokenIndex {

    private static final ClassValue<Map<String, Object>> BY_TYPE = new ClassValue<>() {
        @Override
        protected Map<String, Object> computeValue(Class<?> type) {
            return Arrays.stream(type.getEnumConstants())
                    .collect(Collectors.toUnmodifiableMap(
                            constant -> ((ApiValueEnum) constant).value(), Function.identity()));
        }
    };

    private ApiValueTokenIndex() {}

    static Object lookup(Class<?> type, String token) {
        return BY_TYPE.get(type).get(token);
    }
}
