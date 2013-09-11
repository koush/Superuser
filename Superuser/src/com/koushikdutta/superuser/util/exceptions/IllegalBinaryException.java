package com.koushikdutta.superuser.util.exceptions;

/**
 * @author LiTTle
 *
 * An exception that is thrown when the su binary is not recognized by the application. 
 * 
 */
public class IllegalBinaryException extends Exception {

	/**
	 * This id is generated for future serialization of the exception.
	 */
	private static final long serialVersionUID = 9180923073859286293L;

	/**
	 * Constructs a new illegal binary exception with null as its detail message.
	 */
	public IllegalBinaryException() {
		
	}

	/**
	 * Constructs a new illegal binary exception with the specified detail message.
	 * 
	 * @param detailMessage the error message
	 */
	public IllegalBinaryException(String detailMessage) {
		super(detailMessage);
		
	}

	/**
	 * Constructs a new illegal binary exception with the specified cause and a detail message 
	 * of (cause==null ? null : cause.toString()) 
	 * (which typically contains the class and detail message of cause).
	 * 
	 * @param throwable the cause of this exception
	 */
	public IllegalBinaryException(Throwable throwable) {
		super(throwable);
		
	}

	/**
	 * Constructs a new illegal binary exception with the specified detail message and cause.
	 * 
	 * @param detailMessage the error message
	 * @param throwable the cause of this exception
	 */
	public IllegalBinaryException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
		
	}

}
