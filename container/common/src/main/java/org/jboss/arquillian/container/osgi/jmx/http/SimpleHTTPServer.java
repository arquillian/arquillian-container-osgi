package org.jboss.arquillian.container.osgi.jmx.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.osgi.vfs.VFSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple HTTP server, capable of serving multiple files.
 */
public class SimpleHTTPServer {

    static final Logger _logger = LoggerFactory.getLogger(SimpleHTTPServer.class.getPackage().getName());

    private final Map<String, URL> streams = Collections.synchronizedMap(new HashMap<String, URL>());
    private final List<ClientConnection> clients = new ArrayList<ClientConnection>();
    private final ServerSocket serverSocket;
    private final String canonicalHostName;

    private volatile boolean running = true;

    /**
     * Constructs an HTTP server, which will run on a randomly selected port on the wildcard address.
     */
    public SimpleHTTPServer() throws IOException {
        this(null, InetAddress.getLocalHost().getCanonicalHostName(), 0);
    }

    /**
     * Constructs an HTTP server running on a specified address/port.
     *
     * @param bindAddress the address to bind to.
     * @param port the port to bind to.
     */
    public SimpleHTTPServer(InetAddress bindAddress, int port) throws IOException {
        this(bindAddress, bindAddress.getCanonicalHostName(), port);
    }

    private SimpleHTTPServer(InetAddress bindAddress, String canonicalHostname, int port) throws IOException {
        this.canonicalHostName = canonicalHostname;
        this.serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(bindAddress, port));
    }

    /**
     * Register a stream for serving.
     *
     * @param stream the URL to obtain the stream contents, which will be opened each time the stream is served.
     * @return an HTTP URL that can be used to access the contents of the provided stream.
     */
    public URL serve(URL stream) {
        final String token = UUID.randomUUID().toString();
        streams.put(token, stream);
        try {
            return new URL(String.format("http://%s:%d/%s", canonicalHostName, this.serverSocket.getLocalPort(), token));
        } catch (MalformedURLException e) {
            throw new IllegalStateException("HTTP url could not be parsed.", e);
        }
    }

    private void serve() {
        try {
            while (running) {
                ClientConnection client = new ClientConnection(serverSocket.accept());
                onClientConnect(client);
                client.start();
            }
        } catch (Exception e) {
            runError("Error accepting connection", e);
        }
    }

    private synchronized void onClientConnect(ClientConnection client) {
        if (!running) {
            client.shutdown();
        } else {
            clients.add(client);
        }
    }

    private synchronized void onClientDisconnect(ClientConnection client) {
        clients.remove(client);
    }

    private void runError(String message, Throwable t) {
        if (running) {
            _logger.error(message, t);
        }
    }

    /**
     * Starts listening for client connections.
     */
    public void start() {
        Thread srv = new Thread("Simple HTTP Server") {
            @Override
            public void run() {
                serve();
            }
        };
        srv.setDaemon(true);
        srv.start();
    }

    /**
     * Closes this server, closing the server socket and all in-progress client connections.
     */
    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
        }

        final ArrayList<ClientConnection> runningClients;
        synchronized (this) {
            runningClients = new ArrayList<ClientConnection>(clients);
            clients.clear();
        }
        for (ClientConnection client : runningClients) {
            client.shutdown();
        }
    }

    private class ClientConnection extends Thread {
        private Socket socket;

        ClientConnection(Socket socket) {
            setDaemon(true);
            this.socket = socket;
        }

        public void run() {
            try {
                final BufferedReader input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                final DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                try {
                    final String line = input.readLine();
                    _logger.debug("Incoming request [{}]", line);
                    if ((line == null) || line.length() < 1) {
                        return;
                    }
                    final URL streamUrl = getRequestedFile(line);
                    if (streamUrl != null) {
                        _logger.debug("For [{}] serving {}", line, streamUrl);
                        writeResponse(output, "200 OK");
                        VFSUtils.copyStream(streamUrl.openStream(), output);
                    } else {
                        writeResponse(output, "404 Not Found");
                        _logger.warn("For [{}] no file found", line);
                    }
                } catch (Exception e) {
                    writeResponse(output, "500 Server Error");
                    runError("Error serving file", e);
                } finally {
                    output.flush();
                }
            } catch (Exception e) {
                runError("Error setting up file serving thread", e);
            } finally {
                shutdown();
            }
        }

        private void writeResponse(DataOutputStream output, String responseLine) throws IOException {
            output.writeBytes("HTTP/1.0 ");
            output.writeBytes(responseLine);
            output.writeBytes("\r\n\r\n");
        }

        private URL getRequestedFile(String requestLine) {
            if (requestLine.startsWith("GET")) {
                String[] parts = requestLine.split(" ");
                if ((parts.length >= 2) && parts[1].startsWith("/")) {
                    String token = parts[1].substring(1);
                    return streams.get(token);
                }
            }
            return null;
        }

        public synchronized void shutdown() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
                socket = null;
            }
            onClientDisconnect(this);
        }
    }

}
