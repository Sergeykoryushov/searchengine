package searchengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import searchengine.dto.response.IndexingResponse;

import java.io.IOException;
import java.net.MalformedURLException;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<IndexingResponse> handleInvalidUrlException(InvalidUrlException e) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Данная страница находится за пределами сайтов, указанных в конфигурационном файле")
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<IndexingResponse> handleIOException(IOException ex) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Не удалось установить соединение с сервером: " + ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MalformedURLException.class)
    public ResponseEntity<IndexingResponse> handleMalformedURLException(MalformedURLException ex) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Неверный Url: " + ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<IndexingResponse> handleInterruptedExceptionException(InterruptedException ex) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Внутренняя ошибка сервера: выполнение было прервано " + ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IndexingAlreadyRunningException.class)
    public ResponseEntity<IndexingResponse> handleIndexingAlreadyRunningExceptionException(IndexingAlreadyRunningException ex) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Индексация уже запущена")
                .build();
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IndexingNotRunningException.class)
    public ResponseEntity<IndexingResponse> handleIndexingNotRunningExceptionException(IndexingNotRunningException ex) {
        IndexingResponse response = IndexingResponse.builder()
                .result(false)
                .error("Индексация не запущена")
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
}
