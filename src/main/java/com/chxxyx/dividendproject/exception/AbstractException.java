package com.chxxyx.dividendproject.exception;

public abstract class AbstractException extends RuntimeException{

	abstract public int getStatusCode();
	abstract public String getMessage();
}
