package io.github.hipsterbwp.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small HTTPS GET helpers with timeouts and a size cap; call off the main thread. */
final class Net {
    private Net() {}

    static String get(String address) throws IOException {
        return new String(bytes(address, 1024 * 1024), "UTF-8");
    }

    static byte[] bytes(String address, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("response too large");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
