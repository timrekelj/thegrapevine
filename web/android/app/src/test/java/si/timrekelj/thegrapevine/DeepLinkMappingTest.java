package si.timrekelj.thegrapevine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The magic-link deep link (grapevine://auth/callback?code=...) must be mapped onto
 * the hosted site so the WebView can exchange the code for a session.
 */
public class DeepLinkMappingTest {

    @Test
    public void authCallbackKeepsQuery() {
        android.net.Uri uri = android.net.Uri.parse("grapevine://auth/callback?code=abc&redirect=%2Finvite%3Fcode%3Dx");
        assertEquals("https://thegrapevine.si/auth/callback?code=abc&redirect=%2Finvite%3Fcode%3Dx", MainActivity.mapDeepLink(uri));
    }

    @Test
    public void inviteLinkMapsToPath() {
        android.net.Uri uri = android.net.Uri.parse("grapevine://invite?code=grapevinedemo");
        assertEquals("https://thegrapevine.si/invite?code=grapevinedemo", MainActivity.mapDeepLink(uri));
    }

    @Test
    public void foreignSchemeIsIgnored() {
        assertNull(MainActivity.mapDeepLink(android.net.Uri.parse("https://thegrapevine.si/")));
        assertNull(MainActivity.mapDeepLink(null));
    }
}
