package com.checkai;

import com.checkai.dto.LoginRequest;
import com.checkai.dto.LoginResponse;
import com.checkai.dto.RegisterRequest;
import com.checkai.entity.User;
import com.checkai.mapper.UserMapper;
import com.checkai.service.AuthService;
import com.checkai.util.JwtUtil;
import com.checkai.util.MD5Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldStorePasswordWithBCrypt() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("u1");
        req.setPassword("123456");
        req.setNickname("n1");
        req.setEmail("u1@example.com");
        req.setPhone("13800138000");

        when(userMapper.selectByUsername("u1")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        LoginResponse resp = authService.register(req);
        assertThat(resp.isSuccess()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertThat(inserted.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("123456", inserted.getPassword())).isTrue();
    }

    @Test
    void login_withLegacyMd5_shouldSucceed_andUpgradeToBCrypt() {
        User u = new User();
        u.setId("1");
        u.setUsername("admin");
        u.setNickname("管理员");
        u.setStatus(1);
        u.setPassword(MD5Util.md5("123456"));

        when(userMapper.selectByUsername("admin")).thenReturn(u);
        when(jwtUtil.generateToken("1", "admin")).thenReturn("token-1");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("123456");

        LoginResponse resp = authService.login(req);
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getToken()).isEqualTo("token-1");

        ArgumentCaptor<User> updateCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, atLeastOnce()).updateById(updateCaptor.capture());
        User updated = updateCaptor.getValue();
        assertThat(updated.getId()).isEqualTo("1");
        assertThat(updated.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("123456", updated.getPassword())).isTrue();
    }
}


