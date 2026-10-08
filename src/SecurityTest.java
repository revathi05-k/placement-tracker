// Run this file's main() to check PasswordUtil. No libraries needed.
public class SecurityTest {

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash("correct horse", salt);

        check("right password matches", PasswordUtil.matches("correct horse", salt, hash));
        check("wrong password does not match", !PasswordUtil.matches("wrong horse", salt, hash));
        check("different salt gives a different hash",
                !PasswordUtil.hash("correct horse", PasswordUtil.newSalt()).equals(hash));
        check("hash does not contain the password", !hash.contains("correct"));
        check("session token is long enough", PasswordUtil.newToken().length() >= 40);
        check("session tokens are unique", !PasswordUtil.newToken().equals(PasswordUtil.newToken()));
        check("sha256 of abc is correct", PasswordUtil.sha256Hex("abc")
                .equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));

        System.out.println(failures == 0 ? "\nAll checks passed." : "\n" + failures + " check(s) FAILED.");
    }
}
