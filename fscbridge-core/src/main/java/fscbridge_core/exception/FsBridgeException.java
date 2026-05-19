package fscbridge_core.exception;

public class FsBridgeException extends RuntimeException {

    private final String errorcode;

    public FsBridgeException(String errorcode, String message){
        super(message);
        this.errorcode = errorcode;
    }

    public FsBridgeException(String errorcode, String message, Throwable cause){
        super(message, cause);
        this.errorcode = errorcode;
    }
    public  String getErrorCode(){
        return errorcode;
    }
    @Override
    public  String toString(){
        return "FsBridgeException{" +
                "errorcode='" + errorcode +
                '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
