package dev.venkat.relayhub.security;

import dev.venkat.relayhub.exception.UnsafeWebhookUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class WebhookUrlValidator {

    public void validate(String targetUrl) {

        URI uri;

        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException e) {
            throw new UnsafeWebhookUrlException("Invalid target URL");
        }

        validateScheme(uri);

        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            throw new UnsafeWebhookUrlException(
                    "Target URL must contain a valid hostname"
            );
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);

            for (InetAddress address : addresses) {

                if (isRestrictedAddress(address)) {
                    throw new UnsafeWebhookUrlException(
                            "Target URL resolves to a restricted network address"
                    );
                }
            }

        } catch (UnsafeWebhookUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsafeWebhookUrlException(
                    "Unable to resolve target URL"
            );
        }
    }

    private void validateScheme(URI uri) {

        String scheme = uri.getScheme();

        if (scheme == null
                || (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https"))) {

            throw new UnsafeWebhookUrlException(
                    "Only HTTP and HTTPS URLs are allowed"
            );
        }
    }

    private boolean isRestrictedAddress(InetAddress address) {

        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}