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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class NioTelnetServer {
    private static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
    private static final String MKDIR_COMMAND = "\tmkdir       create directory\n\r";
    private static final String TOUCH_COMMAND = "\ttouch       create file\n\r";
    private static final String CD_COMMAND = "\tcd          change the current directory\n\r";
    private static final String RM_COMMAND = "\trm          delete file or empty directory\n\r";
    private static final String COPY_COMMAND = "\tcopy        copy file / directory. First arg - from, second arg - to\n\r";
    private static final String CAT_COMMAND = "\tcat         displaying 'txt' file contents\n\r";
    private static final String CHANGENICK_COMMAND = "\tchangenick  change user Nickname\n\r";
    private static final Path rootpath = Paths.get("server");

    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    private Map<SocketAddress, String> clients = new HashMap<>();
    private Map<SocketAddress, Path> clientscurpath = new HashMap<>();
    private Path currentpath;

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
        clients.put(channel.getRemoteAddress(), "user" + clients.size()); // присваивем Ник клиенту
        clientscurpath.put(channel.getRemoteAddress(), rootpath); // задаем начальный путь клиенту
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

        currentpath = clientscurpath.get(client); // Получим текущий путь для клиента

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
                sendMessage(getFilesList().concat("\n\r"), selector, client);
            } else if ("mkdir".equals(command[0])) {
                sendMessage(mkDirCommand(command[1]), selector, client);
            } else if ("touch".equals(command[0])) {
                sendMessage(touchCommand(command[1]), selector, client);
            } else if ("cd".equals(command[0])) {
                sendMessage(cdCommand(command[1], client), selector, client);
            } else if ("rm".equals(command[0])) {
                sendMessage(rmCommand(command[1]), selector, client);
            } else if ("copy".equals(command[0])) {
                sendMessage(copyCommand(command[1], command[2]), selector, client);
            } else if ("cat".equals(command[0])) {
                sendMessage(catCommand(command[1]), selector, client);
            } else if ("changenick".equals(command[0])) {
                sendMessage(changenickCommand(command[1], client), selector, client);
            }
        }
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        String msg = clients.get(client) + " | " + clientscurpath.get(client) + ">"; // Имя клиента | текущий путь>
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private String getFilesList() {
        String[] servers = new File(String.valueOf(currentpath)).list();
        return String.join(" ", servers);
    }

    private String mkDirCommand(String dirname) {
        Path path = Paths.get(currentpath + File.separator + dirname).normalize();
        try {
            Files.createDirectory(path);
        } catch(FileAlreadyExistsException e){
            return "ERROR: Directory Already Exists\n\r";
        } catch (IOException e) {
            return "ERROR: IOException\n\r";
        }
        return "Created directory: " + path + "\n\r";
    }

    private String touchCommand(String filename) {
        Path path = Paths.get(currentpath + File.separator + filename).normalize();
        try {
            Files.createFile(path);
        } catch(FileAlreadyExistsException e){
            return "ERROR: File already exists\n\r";
        } catch (IOException e) {
            return "ERROR: IOException\n\r";
        }
        return "Created file: " + path + "\n\r";
    }

    private String cdCommand(String newpath, SocketAddress client) {
        Path path = Paths.get(clientscurpath.get(client) + File.separator + newpath).normalize();
        if (path.toString() == "")  {
            return "Current path is root! (path no change)\n\r";
        }
        if (Files.exists(path)) {
            currentpath = path;
            clientscurpath.put(client, path);
        } else {
            return "ERROR: The path does not exist!\n\r";
        }
        return "";
    }

    private String rmCommand(String fileordirname) {
        Path path = Paths.get(currentpath + File.separator + fileordirname).normalize();
        if (!Files.exists(path)) {
            return "ERROR: The file / directory does not exist!\n\r";
        } else try {
            Files.delete(path);
            return fileordirname + " removed\n\r";
        } catch (IOException e) {
            return "ERROR: Failed to delete the file or directory is not empty!\n\r";
        }
    }

    private String copyCommand(String srcPath, String dstPath) {
        Path sourcePath      = Paths.get(currentpath + File.separator + srcPath).normalize();
        Path destinationPath = Paths.get(currentpath + File.separator + dstPath).normalize();
        try {
            Files.copy(sourcePath, destinationPath);
            return sourcePath + " copied to " + destinationPath + "\n\r";
        } catch(FileAlreadyExistsException e) {
            return "ERROR: The destination file / directory already exist!\n\r";
        } catch (IOException e) {
            return "ERROR: IOException\n\r";
        }
    }

    private String catCommand(String filename) {
        Path path = Paths.get(currentpath + File.separator + filename).normalize();
        if (!Files.exists(path)) {
            return "ERROR: The file does not exist!\n\r";
        }
        List<String> lines = null;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR: IOException\n\r";
        }
        lines.add("");
        return String.join("\n\r", lines);
    }

    private String changenickCommand(String newnick, SocketAddress client) {
        clients.put(client, newnick);
        return "";
    }

    public static void main(String[] args) throws Exception {
        new NioTelnetServer();
    }
}
