package wafflecore;

import wafflecore.tool.Logger;
import wafflecore.WaffleCore;
import wafflecore.message.*;
import wafflecore.model.*;
import wafflecore.util.MessageUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class ConnectionManager {
    private static Logger logger = Logger.getInstance();

    private boolean listening = true;
    private Selector selector;
    private ServerSocketChannel socketChannel;

    private InetSocketAddress host;
    private HashMap<String, SocketChannel> peers = new HashMap<String, SocketChannel>();

    private ByteBuffer buf = ByteBuffer.allocate(256);

    private MessageHandler messageHandler;
    private BlockChainExecutor blockChainExecutor;

    public ConnectionManager(String hostName, int port) {
        this(new InetSocketAddress(hostName, port));
    }

    public ConnectionManager(InetSocketAddress host) {
        this.host = host;

        try {
            this.selector = Selector.open();
            socketChannel = ServerSocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            socketChannel.configureBlocking(false);
            socketChannel.socket().setReuseAddress(true);
            socketChannel.socket().bind(host);
            socketChannel.register(selector, socketChannel.validOps());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        ExecutorService executor = WaffleCore.getExecutor();

        executor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                listen();
                return null;
            }
        });
    }

    public void listen() {
        while (listening) {
            try {
                selector.select();
            } catch (IOException e) {
                return;
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            while (it.hasNext()) {
                SelectionKey key = (SelectionKey) it.next();
                it.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel;
        try {
            socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.socket().setReuseAddress(true);
            socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            logger.log("ACCEPTED: " + socketChannel);

            String peerAddr = socketChannel.getRemoteAddress().toString();
            peers.put(peerAddr, socketChannel);
            newPeer(peerAddr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRead(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();
        String peerStr = "";

        try {
            buf.clear();
            int read = 0;
            while ((read = socketChannel.read(buf)) > 0) {
                buf.flip();
                byte[] bytes = new byte[buf.limit()];
                buf.get(bytes);
                sb.append(new String(bytes));
            }

            peerStr = socketChannel.getRemoteAddress().toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] data = sb.toString().getBytes();
        if (data != null) {
            Envelope env = MessageUtil.deserialize(data);
            messageHandler.handleMessage(env, peerStr);
        }
    }

    /**
     *  Send message to all peers.
     */
    public void asyncBroadcast(byte[] msg) {
        ExecutorService executor = WaffleCore.getExecutor();

        executor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    ByteBuffer buf = ByteBuffer.wrap(msg);
                    for (SelectionKey key : selector.keys()) {
                        if (key.isValid() && key.isWritable() && key.channel() instanceof SocketChannel) {
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            socketChannel.write(buf);
                            buf.rewind();
                        }
                    }
                } catch (Exception e) {
                    // e.printStackTrace();
                }
                return null;
            }
        });
    }

    public void asyncConnect(String hostName, int port) {
        ExecutorService executor = WaffleCore.getExecutor();

        executor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                InetSocketAddress addr = new InetSocketAddress(hostName, port);
                SocketChannel socketChannel = null;

                try {
                    socketChannel = SocketChannel.open();
                    socketChannel.configureBlocking(false);
                    socketChannel.socket().setReuseAddress(true);
                    socketChannel.socket().bind(host);
                    socketChannel.connect(addr);
                    socketChannel.register(selector, socketChannel.validOps());
                    logger.log("CONNECTED: " + socketChannel);

                    String peerAddr = socketChannel.getRemoteAddress().toString();
                    peers.put(peerAddr, socketChannel);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        });
    }

    public void asyncSend(byte[] msg, String addr) {
        ExecutorService executor = WaffleCore.getExecutor();

        executor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    ByteBuffer buf = ByteBuffer.wrap(msg);
                    SocketChannel socketChannel = peers.get(addr);
                    socketChannel.write(buf);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        });
    }

    public void newPeer(String peerAddr) {
        Block genesis = Genesis.getGenesisBlock();

        Hello hello = new Hello(
            getPeers(),
            genesis.getId(),
            blockChainExecutor.getKnownBlockIds()
        );

        Envelope env = hello.packToEnvelope();
        asyncSend(MessageUtil.serialize(env), peerAddr);
    }

    public ArrayList<String> getPeers() {
        ArrayList<String> peerls = new ArrayList<String>();
        // WIP
        return peerls;
    }

    // setter
    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    public void setBlockChainExecutor(BlockChainExecutor blockChainExecutor) {
        this.blockChainExecutor = blockChainExecutor;
    }
}
