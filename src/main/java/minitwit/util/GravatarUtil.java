package minitwit.util;

import org.apache.commons.codec.digest.DigestUtils;

public class GravatarUtil {
    public static String getUrl(String email, int size) {
        String hash = DigestUtils.md5Hex(email.trim().toLowerCase());
        return "https://www.gravatar.com/avatar/" + hash + "?d=identicon&s=" + size;
    }
}