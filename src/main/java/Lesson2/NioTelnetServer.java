package Lesson2;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import Lesson2.IOCmd;

public class NioTelnetServer {
    private static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
    private static final String MKDIR_COMMAND = "\tmkdir       create directory\n\r";
    private static final String TOUCH_COMMAND = "\ttouch       create file\n\r";
    private static final String CD_COMMAND = "\tcd          change the current directory\n\r";
    private static final String RM_COMMAND = "\trm          delete file or empty directory\n\r";
    private static final String COPY_COMMAND = "\tcopy        copy file / directory. First arg - from, second arg - to\n\r";
    private static final String CAT_COMMAND = "\tcat         displaying 'txt' file contents\n\r";
    private static final String CHANGENICK_COMMAND = "\tchangenick  change user Nickname\n\r";


    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    private Map<SocketAddress, User> clients = new HashMap<>();



    public NioTelnetServer() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5679));
        server.configureBlocking(false);
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client connected. IP:" + channel.getRemoteAddress());
        clients.put(channel.getRemoteAddress(), new User("user" + clients.size())); // присваивем Ник клиенту, задаем каталог

        channel.register(selector, SelectionKey.OP_READ, "skjghksdhg");
        channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\n\r".getBytes(StandardCharsets.UTF_8)));
    }


    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);

        if (readBytes < 0) {
            channel.close();
            return;
        } else  if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        // TODO: 21.06.2021
        // touch (filename) - создание файла
        // mkdir (dirname) - создание директории
        // cd (path | ~ | ..) - изменение текущего положения
        // rm (filename / dirname) - удаление файла / директории
        // copy (src) (target) - копирование файлов / директории
        // cat (filename) - вывод содержимого текстового файла
        // changenick (nickname) - изменение имени пользователя

        // добавить имя клиента

        Path currentpath = clients.get(client).getCurrentpath(); // Получим текущий путь для клиента

        if (key.isValid()) {
            String[] command = sb.toString()
                    .replace("\n", "")
                    .replace("\r", "")
                    .split(" ");
            if ("--help".equals(command[0])) {
                sendMessage(LS_COMMAND
                            + MKDIR_COMMAND
                            + TOUCH_COMMAND
                            + CD_COMMAND
                            + RM_COMMAND
                            + COPY_COMMAND
                            + CAT_COMMAND
                            + CHANGENICK_COMMAND, selector, client);
            } else if ("ls".equals(command[0])) {
                sendMessage(IOCmd.getFilesList(currentpath).concat("\n\r"), selector, client);
            } else if ("mkdir".equals(command[0])) {
                sendMessage(IOCmd.mkDir(currentpath, command[1]), selector, client);
            } else if ("touch".equals(command[0])) {
                sendMessage(IOCmd.crFile(currentpath, command[1]), selector, client);
            } else if ("cd".equals(command[0])) {
                sendMessage(cdCommand(command[1], client), selector, client);
            } else if ("rm".equals(command[0])) {
                sendMessage(IOCmd.delFileOrDir(currentpath, command[1]), selector, client);
            } else if ("copy".equals(command[0])) {
                sendMessage(IOCmd.copyFileOrDir(currentpath, command[1], command[2]), selector, client);
            } else if ("cat".equals(command[0])) {
                sendMessage(IOCmd.catFile(currentpath, command[1]), selector, client);
            } else if ("changenick".equals(command[0])) {
                sendMessage(changenickCommand(command[1], client), selector, client);
            }
        }
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        // Имя клиента | текущий путь>
        String msg = clients.get(client).getNickname() + " | "
                     + String.valueOf(clients.get(client).getCurrentpath()).replace(String.valueOf(clients.get(client).getRootpath()), "~")
                     + ">";
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }


    private String cdCommand(String newpath, SocketAddress client) {
        Path path = Paths.get(clients.get(client).getCurrentpath() + File.separator + newpath).normalize();
        if ("..".equals(newpath) & clients.get(client).getCurrentpath() == clients.get(client).getRootpath())  {
            return "Current path is root! (path no change)\n\r";
        }
        if ("~".equals(newpath)) {
            clients.get(client).setCurrentpath(clients.get(client).getRootpath());
            return "";
        }
        if (Files.exists(path)) {
            clients.get(client).setCurrentpath(path);
        } else {
            return "ERROR: The path does not exist!\n\r";
        }
        return "";
    }


    private String changenickCommand(String newnick, SocketAddress client) {
        clients.get(client).setNickname(newnick);
        return "";
    }

    public static void main(String[] args) throws Exception {
        new NioTelnetServer();
    }
}
