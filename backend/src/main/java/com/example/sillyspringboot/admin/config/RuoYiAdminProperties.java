package com.example.sillyspringboot.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.ruoyi-admin")
public class RuoYiAdminProperties {

    private String username = "admin";
    private String nickName = "酒馆运营后台";
    private String encodedPassword;
    private String role = "super-admin";
    private List<String> roles = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();
    private String jwtSecret;
    private int jwtExpireHours = 720;
    private boolean captchaEnabled = false;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getEncodedPassword() {
        return encodedPassword;
    }

    public void setEncodedPassword(String encodedPassword) {
        this.encodedPassword = encodedPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getJwtExpireHours() {
        return jwtExpireHours;
    }

    public void setJwtExpireHours(int jwtExpireHours) {
        this.jwtExpireHours = jwtExpireHours;
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    public static class Account {
        private String username;
        private String nickName;
        private String encodedPassword;
        private String role = "support";
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public String getEncodedPassword() {
            return encodedPassword;
        }

        public void setEncodedPassword(String encodedPassword) {
            this.encodedPassword = encodedPassword;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
