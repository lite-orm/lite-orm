package org.liteorm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * LiteORM配置管理
 * 
 * 配置优先级：
 * 1. lite-orm.properties（如果存在）
 * 2. 默认配置
 * 
 * 设计原则：
 * - 约定优于配置
 * - 零配置即可使用
 * - 配置文件可选
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class LiteOrmConfig {
    
    private static final String CONFIG_FILE = "lite-orm.properties";
    
    // 默认配置
    private static final String DEFAULT_CODE_GENERATOR = "annotation";
    private static final String DEFAULT_XML_LOCATION = "mapper";
    private static final String DEFAULT_GENERATED_PACKAGE = "";
    
    private final Properties properties;
    
    private static LiteOrmConfig instance;
    
    private LiteOrmConfig() {
        this.properties = loadConfig();
    }
    
    public static LiteOrmConfig getInstance() {
        if (instance == null) {
            instance = new LiteOrmConfig();
        }
        return instance;
    }
    
    /**
     * 代码生成器类型
     * annotation: 使用@Select等注解
     * xml: 使用XML配置文件
     */
    public String getCodeGenerator() {
        return properties.getProperty("lite-orm.code-generator", DEFAULT_CODE_GENERATOR);
    }
    
    /**
     * XML配置文件目录
     */
    public String getXmlConfigLocation() {
        return properties.getProperty("lite-orm.xml-location", DEFAULT_XML_LOCATION);
    }
    
    /**
     * 生成代码的包名前缀
     */
    public String getGeneratedPackage() {
        return properties.getProperty("lite-orm.generated-package", DEFAULT_GENERATED_PACKAGE);
    }
    
    /**
     * 是否启用调试模式
     */
    public boolean isDebugEnabled() {
        return Boolean.parseBoolean(properties.getProperty("lite-orm.debug", "false"));
    }
    
    /**
     * 加载配置文件
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        
        // 尝试从classpath加载配置文件
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                System.out.println("Loaded LiteORM config from " + CONFIG_FILE);
            } else {
                System.out.println("No " + CONFIG_FILE + " found, using default configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to load " + CONFIG_FILE + ": " + e.getMessage());
        }
        
        return props;
    }
}
