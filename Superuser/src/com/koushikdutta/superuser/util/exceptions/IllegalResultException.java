/**
 * 
 */
package com.koushikdutta.superuser.util.exceptions;

/**
 * @author LiTTle
 *
 * An exception that is thrown when a method returns something different from 
 * what we expect.
 */
public class IllegalResultException extends Exception {

	/**
	 * This id is generated for future serialization of the exception.
	 */
	private static final long serialVersionUID = -7857600527827904095L;

	/** 
	 * Constructs a new illegal result exception with null as its detail message.
	 */
	public IllegalResultException() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Constructs a new illegal result exception with the specified detail message.
	 * 
	 * @param detailMessage the error message
	 */
	public IllegalResultException(String detailMessage) {
		super(detailMessage);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Constructs a new illegal result exception with the specified cause and a detail message 
	 * of (cause==null ? null : cause.toString()) 
	 * (which typically contains the class and detail message of cause).
	 * 
	 * @param throwable the cause of this exception
	 */
	public IllegalResultException(Throwable throwable) {
		super(throwable);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Constructs a new illegal result exception with the specified detail message and cause.
	 * 
	 * @param detailMessage the error message
	 * @param throwable the cause of this exception
	 */
	public IllegalResultException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
		// TODO Auto-generated constructor stub
	}

}
