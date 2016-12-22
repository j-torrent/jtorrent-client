package org.jtorrent.client.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Utils {
    public static List<String> splitBy(String string, int chunkSize) {
        List<String> answer = new ArrayList<>();
        int current = 0;
        while (current < string.length()) {
            answer.add(string.substring(current, current + chunkSize));
            current += chunkSize;
        }
        return answer;
    }

    public static List<byte[]> splitBy(byte[] bytes, int chunkSize) {
        List<byte[]> answer = new ArrayList<>();
        int current = 0;
        while (current < bytes.length) {
            byte[] chunk = Arrays.copyOfRange(bytes, current, current + chunkSize);
            answer.add(chunk);
            current += chunkSize;
        }
        return answer;
    }
}
