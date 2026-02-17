package com.aiinpocket.btctrade.security;

import com.aiinpocket.btctrade.model.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 自訂的認證主體（Principal），包裝了原始的 OidcUser 和資料庫中的 AppUser。
 * Controller 可以透過 @AuthenticationPrincipal 取得此物件，
 * 進而存取 getUserId() 和 getAppUser() 來獲取使用者資訊。
 */
public class AppUserPrincipal implements OidcUser {

    private final OidcUser delegate;
    private final AppUser appUser;

    public AppUserPrincipal(OidcUser delegate, AppUser appUser) {
        this.delegate = delegate;
        this.appUser = appUser;
    }

    /** 取得資料庫中的 AppUser 實體 */
    public AppUser getAppUser() {
        return appUser;
    }

    /** 取得使用者的資料庫主鍵 ID */
    public Long getUserId() {
        return appUser.getId();
    }

    // ===== 以下方法全部委派給原始的 OidcUser =====

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole()));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
