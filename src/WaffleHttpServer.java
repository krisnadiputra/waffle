import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class WaffleHttpServer extends Thread {
    public WaffleHttpServer() {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    }

    public void run() {
        try {
            server.createContext("/", new WaffleHttpHandler());

            server.setExecutor(null);
            server.start();
            System.out.println("Started Server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        server.close();
    }
}

class WaffleHttpHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange arg0) throws IOException {
        InputStream is = arg0.getRequestBody();
        byte[] bytes = new byte[1024];
        while ((len = is.read(bytes)) >= 0) {

        }

        arg0.getResponseHeaders().add("Content-Type", "text/html");
        arg0.sendResponseHeaders(200, file.length());
        OutputStream os = arg0.getResponseBody();

        int len = 0;
        while ((len = is.read(bytes)) >= 0) {
            os.write(bytes, 0, len);
        }
        os.flush();

        os.close();
        is.close();
    }
}

// class WaffleHttpHandler implements HttpHandler {
//     @Override
//     public void handle(HttpExchange arg0) throws IOException {
//         String path = arg0.getRequestURI().getPath();
//         if (path.equals("/")) {
//             path = "/index.html";
//         }
//         File file = new File(".", path);
//         arg0.getResponseHeaders().add("Content-Type", "text/html");
//         arg0.sendResponseHeaders(200, file.length());
//         InputStream is = new FileInputStream(file);
//         OutputStream os = arg0.getResponseBody();

//         byte[] bytes = new byte[1024];
//         int len = 0;
//         while((len = is.read(bytes)) >= 0){
//             os.write(bytes, 0, len);
//         }
//         os.flush();

//         os.close();
//         is.close();
//     }
// }
