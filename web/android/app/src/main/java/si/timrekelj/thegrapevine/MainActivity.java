package si.timrekelj.thegrapevine;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Route the magic-link deep link (grapevine://auth/callback?...) into the
        // hosted site so Supabase can exchange the code for a session. The site's
        // own /auth/callback route then finishes the sign-in and redirects in-app.
        handleNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNewIntent(intent);
    }

    static final String SITE_URL = "https://thegrapevine.si";

    static String mapDeepLink(android.net.Uri data) {
        if (data == null || !"grapevine".equals(data.getScheme())) return null;
        String target = SITE_URL + data.getEncodedPath();
        if (data.getEncodedQuery() != null) target += "?" + data.getEncodedQuery();
        return target;
    }

    private void handleNewIntent(android.content.Intent intent) {
        android.net.Uri data = intent != null ? intent.getData() : null;
        String target = mapDeepLink(data);
        if (target == null) return;

        // Replace the back stack so "back" never returns to the raw deep link.
        android.content.Intent webIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target));
        webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(webIntent);
    }

    @Override
    public void load() {
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        plugins.add(com.getcapacitor.plugin.app.AppPlugin.class);
        plugins.add(com.getcapacitor.plugin.browser.BrowserPlugin.class);
        plugins.add(com.getcapacitor.plugin.clipboard.ClipboardPlugin.class);
        plugins.add(com.getcapacitor.plugin.share.SharePlugin.class);
        plugins.add(com.getcapacitor.plugin.splashscreen.SplashScreenPlugin.class);
        plugins.add(com.getcapacitor.plugin.statusbar.StatusBarPlugin.class);
        registerPlugins(plugins);
        super.load();
    }
}
