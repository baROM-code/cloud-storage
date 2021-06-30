package Lesson3.netty.handlers;

import Lesson3.netty.IOCmd;
import Lesson3.netty.User;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class StringInputHandler extends ChannelInboundHandlerAdapter {

    private static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
    private static final String MKDIR_COMMAND = "\tmkdir       create directory\n\r";
    private static final String TOUCH_COMMAND = "\ttouch       create file\n\r";
    private static final String CD_COMMAND = "\tcd          change the current directory\n\r";
    private static final String RM_COMMAND = "\trm          delete file or empty directory\n\r";
    private static final String COPY_COMMAND = "\tcopy        copy file / directory. First arg - from, second arg - to\n\r";
    private static final String CAT_COMMAND = "\tcat         displaying 'txt' file contents\n\r";
    private static final String CHANGENICK_COMMAND = "\tchangenick  change user Nickname\n\r";

    private static Map<ChannelId, User> clients = new HashMap<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client connected: " + ctx.channel() + "   user" + clients.size());
        clients.put(ctx.channel().id(), new User("user" + clients.size()));
        ctx.writeAndFlush("Hello user!\n");
        ctx.writeAndFlush("Enter --help for support info\n\r");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client disconnected: " + ctx.channel());
        clients.remove(ctx.channel().id());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Path currentpath = clients.get(ctx.channel().id()).getCurrentpath(); // Получим текущий путь для клиента
        String outmsg = "";
        String[] command = String.valueOf(msg)
                .replace("\n", "")
                .replace("\r", "")
                .split(" ");
        if ("--help".equals(command[0])) {
            outmsg = LS_COMMAND
                    + MKDIR_COMMAND
                    + TOUCH_COMMAND
                    + CD_COMMAND
                    + RM_COMMAND
                    + COPY_COMMAND
                    + CAT_COMMAND
                    + CHANGENICK_COMMAND;
        } else if ("ls".equals(command[0])) {
            outmsg = IOCmd.getFilesList(currentpath).concat("\n\r");
        } else if ("mkdir".equals(command[0])) {
            outmsg = IOCmd.mkDir(currentpath, command[1]);
        } else if ("touch".equals(command[0])) {
            outmsg = IOCmd.crFile(currentpath, command[1]);
        } else if ("cd".equals(command[0])) {
            outmsg = cdCommand(command[1], ctx.channel().id());
        } else if ("rm".equals(command[0])) {
            outmsg = IOCmd.delFileOrDir(currentpath, command[1]);
        } else if ("copy".equals(command[0])) {
            outmsg = IOCmd.copyFileOrDir(currentpath, command[1], command[2]);
        } else if ("cat".equals(command[0])) {
            outmsg = IOCmd.catFile(currentpath, command[1]);
        } else if ("changenick".equals(command[0])) {
            changenickCommand(command[1], ctx.channel().id());
        }
        ctx.writeAndFlush(outmsg);
        // Имя клиента | текущий путь>
        String prompt = clients.get(ctx.channel().id()).getNickname() + " | "
                + String.valueOf(clients.get(ctx.channel().id()).getCurrentpath()).replace(String.valueOf(clients.get(ctx.channel().id()).getRootpath()), "~")
                + ">";
        ctx.writeAndFlush(prompt);
    }

    private String cdCommand(String newpath, ChannelId client) {
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

    private void changenickCommand(String newnick, ChannelId client) {
        clients.get(client).setNickname(newnick);
    }

}
