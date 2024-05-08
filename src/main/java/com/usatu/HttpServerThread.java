package com.usatu;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

public class HttpServerThread extends Thread {

    String ROOT_DIR = "www";        // Корневой каталог веб-сервера для чтения веб-страниц
    String POST_DIR = "posted";     // Папка для записи переданных с помощью метода POST данных
    // Максимальный размер данных, передаваемый веб-серверу в запросе (в байтах)
    int POST_MAX_SIZE = 1024 * 1024 * 2;    // 2Mb
    int KEEP_ALIVE = 10;     // Таймаут HTTP соединения (TCP сессии между веб-сервером и клиентом) в сек.

    // Список доступных кодов HTTP
    String OK = "200 OK";
    String NOT_FOUND = "404 Not Found";
    String BAD_REQUEST = "400 Bad Request";
    String SERVER_ERROR = "500 Internal Server Error";

    // Поддерживаемые веб-сервером типы файлов и соответствующие расширения
    String[] MIME_TYPES = {
            "text/html",
            "text/xml",
            "application/json",
            "application/xml",
    };

    DataInputStream data_in;
    DataOutputStream data_out;
    Socket socket;

    public HttpServerThread(Socket socket) throws IOException {
        this.socket = socket;
        // Определяем входной и выходной потоки сокета для обмена данными с клиентом и
        // создаем потоки ввода и вывода данных (в байтах)
        this.data_in  = new DataInputStream (socket.getInputStream());
        this.data_out = new DataOutputStream(socket.getOutputStream());
        start(); // вызываем run()
    }

    @Override
    public void run()
    {
        System.out.printf("New Thread-%d created!%n", this.threadId());
        try {
            String request;
            // Запускаем цикл обмена данными с клиентом
            while(true) {
                byte[] buf = new byte[POST_MAX_SIZE];    // буфер для чтения/записи байт данных из потоков ввода/вывода
                int bytesRead;  // переменная для хранения количества считанных байт
                // Ожидание сообщения от клиента. После получения сообщения, данные будут записаны в буфер
                bytesRead = data_in.read(buf);
                // Если data_in.read возвращает -1, то поток чтения завершен,
                // вероятно, браузер завершил соединение по таймауту
                if (bytesRead == -1) {
                    System.out.println("Keep-alive timeout.");
                    break;
                }
                request = new String(buf, StandardCharsets.UTF_8);   // преобразуем клиентский запрос в строку для парсинга
                data_out.write(readClientRequest(request));                 // парсим запрос и возвращаем результат
                // Очищаем поток вывода
                data_out.flush();
            }
        } catch(Exception e) {
            // Обработка ошибок сервера
            System.out.println("Server Error : " + e);
            try {
                data_out.write(showError(SERVER_ERROR));
                data_out.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // Создание заголовка HTTP-ответа
    public byte[] makeHttpHeader(String code, long length, String content_type) {
        String header = String.format(
                        "HTTP/1.1 %s\r\n" +
                        "Server: java_lab_4\r\n" +
                        "Content-Type: %s; charset=utf-8\r\n" +
                        "Content-Length: %d\r\n" +
                        "Keep-Alive: timeout=%d, max=100\r\n" +
                        "Connection: Keep-Alive\r\n\r\n",
                code, content_type, length, KEEP_ALIVE);
        return header.getBytes(StandardCharsets.UTF_8);
    }

    // Чтение контента
    public byte[] getContentPage(String dir, String path, String contentType) {
        try {
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();     // поток вывода запроса в байтах
            File contentFile = new File(dir + "/" + path);
            if(contentFile.isFile()){
                byte[] content = Files.readAllBytes(contentFile.toPath());
                if (content.length > 0) {
                    responseStream.write(makeHttpHeader(OK, content.length, contentType));  // пишем заголовок
                    responseStream.write(content);         // пишем тело ответа
                    return responseStream.toByteArray();    // возвращаем результат
                }
            }
            return showError(NOT_FOUND);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return showError(SERVER_ERROR);
        }
    }

    // Парсинг клиентского запроса
    public byte[] readClientRequest(String req) {
        // Разделяем блок заголовков и контент
        String[] reqData = req.split("\r\n\r\n");
        // Разбиваем заголовки по отдельным строкам
        String[] headData = reqData[0].split("\r\n");
        // Если массив не пуст, парсим стартовую строку
        if (headData.length > 0) {
            String[] mainHeaderData = headData[0].split(" ");
            String reqType = mainHeaderData[0];     // HTTP-метод
            String reqPath = mainHeaderData[1];     // Путь к запрашиваемому контенту
            switch (reqType) {
                case "GET":
                    if (reqPath.equals("/")) reqPath = "/index.html";   // устанавливаем контент по умолчанию
                    return getContentPage(ROOT_DIR, reqPath, "text/html");                     // возвращаем запрашиваемый контент
                case "POST":
                    // Получение значения заголовка Content-Type
                    String contentType = getHeadData(headData, "Content-Type");

                    // Проверяем есть ли заголовок в массиве допустимых
                    if(Arrays.asList(MIME_TYPES).contains(contentType)){
                        // получаем тип документа: xml, json и т.п.
                        String type = contentType.split("/")[1];

                        // Записываем данные из тела post-запроса в созданный файл
                        try {
                            String pathToDir = ROOT_DIR + "/" + POST_DIR;
                            // создаём файл
                            File file = createFile(pathToDir, type);
                            FileOutputStream fileOut = new FileOutputStream(file);

                            int contentLength = Integer.parseInt(getHeadData(headData, "Content-Length"));
                            fileOut.write(reqData[1].getBytes(), 0, contentLength);

                            fileOut.close();
                            return getContentPage(pathToDir,"/" + file.getName(), contentType);
                        } catch (IOException e) {
                            return showError(SERVER_ERROR);
                        }
                    }
                default:
                    return showError(BAD_REQUEST);
            }
        }
        return showError(BAD_REQUEST);
    }

    private File createFile(String path, String type) throws IOException {
        String fileName = UUID.randomUUID() + "." + type;
        return Files.createFile(Paths.get(path, fileName)).toFile();
    }

    private String getHeadData(String[] headData, String search){
        for(String dataRow : headData){
            if(dataRow.contains(search)){
                return dataRow.split(":")[1].strip();
            }
        }
        return null;
    }

    private byte[] showError(String error){
        try(ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            byte[] content = error.getBytes();
            byteStream.write(makeHttpHeader(OK, content.length, "text/html"));
            byteStream.write(content);
            return byteStream.toByteArray();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return makeHttpHeader(OK, 0, "text/html");
        }
    }
}
