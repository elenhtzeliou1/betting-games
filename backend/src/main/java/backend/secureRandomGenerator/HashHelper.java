package backend.secureRandomGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Hash helper used by both SRG and Worker
public class HashHelper {
    public static String sha256(String input){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        }catch (Exception e){
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

}
