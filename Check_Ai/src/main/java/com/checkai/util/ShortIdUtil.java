package com.checkai.util;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 短ID生成工具类，用于生成更短、更具可读性的ID
 */
public class ShortIdUtil {
    
    // 字符集，去除了容易混淆的字符：0, O, 1, l
    private static final String CHARACTERS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BASE = CHARACTERS.length();
    private static final Random RANDOM = new SecureRandom();
    
    /**
     * 生成指定长度的短ID
     * @param length 短ID长度
     * @return 短ID字符串
     */
    public static String generateShortId(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("长度必须大于0");
        }
        
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(BASE)));
        }
        return sb.toString();
    }
    
    /**
     * 生成默认长度（8位）的短ID
     * @return 8位短ID字符串
     */
    public static String generateShortId() {
        return generateShortId(8);
    }
    
    /**
     * 生成带时间戳的短ID，格式：时间戳_随机字符串
     * @param length 随机字符串长度
     * @return 带时间戳的短ID
     */
    public static String generateTimestampShortId(int length) {
        long timestamp = System.currentTimeMillis();
        String randomPart = generateShortId(length);
        return timestamp + "_" + randomPart;
    }
    
    /**
     * 生成带时间戳的默认长度（8位随机字符串）短ID
     * @return 带时间戳的短ID
     */
    public static String generateTimestampShortId() {
        return generateTimestampShortId(8);
    }
}