package com.example.sillyspringboot.shared.net;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

public final class OutboundUrlGuard {

    private OutboundUrlGuard() {
    }

    public static URI requirePublicHttpUrl(String rawUrl, String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank() ? "外部地址不安全" : errorMessage;
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().trim().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || host.isBlank()
                    || uri.getUserInfo() != null
                    || uri.getPort() == 0) {
                throw blocked(message);
            }
            if ("localhost".equals(host)
                    || host.endsWith(".localhost")
                    || host.endsWith(".local")
                    || "metadata.google.internal".equals(host)) {
                throw blocked(message);
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw blocked(message);
            }
            for (InetAddress address : addresses) {
                if (isBlocked(address)) {
                    throw blocked(message);
                }
            }
            return uri;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw blocked(message);
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }
        return bytes.length == 16 && ((bytes[0] & 0xfe) == 0xfc);
    }

    private static BusinessException blocked(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
