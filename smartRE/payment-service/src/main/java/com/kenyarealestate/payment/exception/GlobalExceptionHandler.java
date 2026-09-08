package com.kenyarealestate.payment.exception;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*; import java.time.LocalDateTime; import java.util.Map;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * A request for a path this service does not serve.
     *
     * <p>Without this, Spring's NoResourceFoundException falls through to the catch-all
     * below and is reported as 500 "An unexpected error occurred." That is worse than
     * untidy: a client typing the wrong URL then shows up in dashboards as a server
     * fault, which inflates the error rate the deployment is judged on and wakes
     * somebody for a mistake no server made.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noSuchRoute(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "No such endpoint.");
    }
    @ExceptionHandler(RuntimeException.class) public ResponseEntity<Map<String,Object>> r(RuntimeException e) { return build(HttpStatus.BAD_REQUEST,e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> v(MethodArgumentNotValidException e) {
        String m=e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST,m);
    }
    private ResponseEntity<Map<String,Object>> build(HttpStatus s, String msg){
        return ResponseEntity.status(s).body(Map.of("timestamp",LocalDateTime.now().toString(),"status",s.value(),"error",msg));
    }
}
