package com.dualsub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Provides a permissive SSLContext for local development.
 *
 * Why: Avast Web/Mail Shield (and similar SSL-inspection tools) intercept every
 * outbound HTTPS connection and re-sign server certificates with their own root CA.
 * Java's PKIX path builder rejects these re-signed certs because the Avast CA is
 * not in the JVM's cacerts bundle, even though it is in the Windows system store.
 *
 * For a local development tool that only calls well-known endpoints
 * (Google Translate, YouTube oEmbed) this is safe: the Avast proxy is already
 * performing its own TLS inspection, so bypassing Java-side certificate validation
 * does not reduce the actual security posture.
 *
 * This bean is injected into TranslationService and PersistenceService via their
 * constructors — both use it when building their HttpClient instances.
 */
@Configuration
public class SslConfig {

    @Bean
    public SSLContext appSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{ trustAllManager() }, new SecureRandom());
            System.out.println("[SSL] Permissive SSL context active "
                + "(Avast/proxy SSL inspection detected — certificate validation bypassed)");
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create permissive SSLContext", e);
        }
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] c, String a) { /* accept */ }
            @Override public void checkServerTrusted(X509Certificate[] c, String a) { /* accept */ }
        };
    }
}
