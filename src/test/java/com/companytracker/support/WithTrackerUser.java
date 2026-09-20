package com.companytracker.support;

import com.companytracker.domain.UserAccount;
import com.companytracker.security.TrackerUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithTrackerUser.Factory.class)
public @interface WithTrackerUser {
    long id() default 1L;
    String email() default "user@test.com";
    String role() default "USER";
    boolean enabled() default true;

    class Factory implements WithSecurityContextFactory<WithTrackerUser> {
        @Override
        public SecurityContext createSecurityContext(WithTrackerUser annotation) {
            UserAccount user = new UserAccount();
            user.setEmail(annotation.email());
            user.setPasswordHash("hash");
            user.setRole(com.companytracker.domain.UserRole.valueOf(annotation.role()));
            user.setEnabled(annotation.enabled());
            user.setSecurityStamp(UUID.randomUUID());
            try {
                var f = UserAccount.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(user, annotation.id());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            TrackerUserDetails details = new TrackerUserDetails(user);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    details, null, details.getAuthorities()));
            return context;
        }
    }
}
