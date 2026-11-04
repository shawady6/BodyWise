import java.io.FileInputStream;
import java.security.KeyStore;

/** Opens a private key exactly like Gradle/AGP does. Exit 0 = key opened. Prints nothing secret. */
public class KeyCheck {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance(a[0]);
        try (FileInputStream in = new FileInputStream(a[1])) {
            ks.load(in, System.getenv("CHECK_STORE_PASS").toCharArray());
        }
        System.exit(ks.getKey(a[2], System.getenv("CHECK_KEY_PASS").toCharArray()) != null ? 0 : 2);
    }
}
