package com.checkai.util;

import com.checkai.entity.User;

/**
 * 当前用户信息存储工具类，基于ThreadLocal实现
 */
public class CurrentUserHolder {

    /**
     * 存储当前用户信息的ThreadLocal
     */
    private static final ThreadLocal<User> currentUser = ThreadLocal.withInitial(() -> null);

    /**
     * 设置当前用户信息
     * @param user 用户信息
     */
    public static void set(User user) {
        currentUser.set(user);
    }

    /**
     * 获取当前用户信息
     * @return 当前用户信息
     */
    public static User get() {
        return currentUser.get();
    }

    /**
     * 获取当前用户ID
     * @return 当前用户ID
     */
    public static String getUserId() {
        User user = currentUser.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户名
     * @return 当前用户名
     */
    public static String getUsername() {
        User user = currentUser.get();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 清除当前用户信息
     */
    public static void clear() {
        currentUser.remove();
    }

    /**
     * 判断当前用户是否已登录
     * @return 是否已登录
     */
    public static boolean isLoggedIn() {
        return currentUser.get() != null;
    }
}