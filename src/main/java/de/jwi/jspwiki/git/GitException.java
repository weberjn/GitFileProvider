package de.jwi.jspwiki.git;

public class GitException extends Exception
{
	public GitException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public GitException(String message)
	{
		super(message);
	}

	public GitException(Throwable cause)
	{
		super(cause);
	}

}
