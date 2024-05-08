package com.usatu;
import java.io.*;
import java.net.*;


public class SimpleHttpServer {

    public static final String HOST = "0.0.0.0";    // данное описание адреса означает, что сервер будет принимать соединения на всех доступных интерфейсах
    public static final int PORT = 8000;

    public static void main(String[] args) throws IOException {
        // Создаем объект серверного сокета для 1 клиента и привязываем к заданному хосту и порту
        ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getByName(HOST));
        System.out.printf("Server is running on %s:%d, please, press ctrl+c to stop%n", HOST, PORT);
        try {
            while (true) {
                // Ожидаем соединения от клиента (дальнейшее выполнение кода блокируется)
                Socket socket = server.accept();
                System.out.println("New client connected!");
                try {
                    new HttpServerThread(socket); // создаем новое соединение в отдельном потоке
                } catch (Exception e) {
                    socket.close();
                    // Вывод исключений
                    System.out.println("Exception : " + e);
                }
            }
        } finally {
            // Закрываем сокет-сервер
            server.close();
        }
    }


}
