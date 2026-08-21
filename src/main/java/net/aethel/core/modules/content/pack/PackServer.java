package net.aethel.core.modules.content.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uretilen paketi kendi ip:port adresimizden servis eder. Harici bir host'a bagimli
 * olmamak icin JDK'nin gomulu HTTP sunucusu kullanilir; ek kutuphane gerekmez.
 */
public final class PackServer {

    private final Logger log;
    private final Executor executor;
    private HttpServer server;

    public PackServer(Logger log, Executor executor) {
        this.log = log;
        this.executor = executor;
    }

    /**
     * Sunucuyu baslatir. Istekler sanal thread havuzunda islenir; boyle bir istek
     * ana thread'e hicbir zaman dokunmaz.
     */
    public boolean start(String bind, int port, File packFile) {
        stop();
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.setExecutor(executor);
            server.createContext("/generated.zip", exchange -> serve(exchange, packFile));
            server.createContext("/", exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });
            server.start();
            log.info("Kaynak paketi sunuluyor: http://" + bind + ":" + port + "/generated.zip");
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Kaynak paketi sunucusu baslatilamadi (port " + port + ")", e);
            return false;
        }
    }

    private void serve(com.sun.net.httpserver.HttpExchange exchange, File packFile) throws IOException {
        if (!packFile.exists()) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        byte[] data = Files.readAllBytes(packFile.toPath());
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        // Hash her uretimde degistigi icin uzun onbellek guvenlidir; degismeyen pack
        // istemcide zaten yeniden indirilmez.
        exchange.getResponseHeaders().add("Cache-Control", "public, max-age=86400");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }
}
