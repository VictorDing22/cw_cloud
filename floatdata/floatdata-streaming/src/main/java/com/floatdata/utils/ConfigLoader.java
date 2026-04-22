package com.floatdata.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置加载器
 */
public class ConfigLoader {
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "application.properties";

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("配置文件不存在: " + CONFIG_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败", e);
        }
    }

    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String getString(String key) {
        return properties.getProperty(key);
    }

    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    public static long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    public static double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
}
