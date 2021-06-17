package Lesson1;

import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
	// TODO: 14.06.2021
	// организовать корректный вывод статуса
	// подумать почему так реализован цикл в ClientHandler -
	//  (size + (buffer.length - 1)) / (buffer.length) здесь выичсляется
	//  количество итераций цикла с учетом размера файла относительно размера буфера
	//  т.е. сколько раз должен пройти цикл чтоб считать весь файл частями равными размеру буфера
	public Server() {
		ExecutorService service = Executors.newFixedThreadPool(4);
		try (ServerSocket server = new ServerSocket(5678)){
			System.out.println("Server started");
			while (true) {
				service.execute(new ClientHandler(server.accept()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new Server();
	}
}
