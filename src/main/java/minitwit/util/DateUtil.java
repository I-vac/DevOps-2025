package minitwit.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    private static final DateTimeFormatter formatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd @ HH:mm")
            .withZone(ZoneId.systemDefault());

    public static String format(long timestamp) {
        return formatter.format(Instant.ofEpochSecond(timestamp));
    }
}