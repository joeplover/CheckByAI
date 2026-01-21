-- 使用数据库
USE check_ai;

-- 更新所有用户的密码为123456（使用MD5加密）
-- 123456的MD5哈希值为：e10adc3949ba59abbe56e057f20f883e
UPDATE `user` SET password = 'e10adc3949ba59abbe56e057f20f883e' WHERE id IN ('1', '2', '3');

-- 验证更新结果
SELECT username, password FROM `user` WHERE id IN ('1', '2', '3');
