package org.lite.parser.xml;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qingbozhang
 * @since 2024/11/13 16:14
 */
@Data
public class GlobalSql {
    // namespace#id,sqlRoot
    private static final Map<String, FragmentRoot> sqlRootMap = new ConcurrentHashMap<>();


}
