package lib;

import java.io.Serializable;

/**
 * Class 
 * 
 * 
 * A class that holds the log entry information
 *
 */
public class LogEntries implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int term; 
	private int index; 
	private int command;

	public int getTerm() {
		return term;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getCommand() {
		return command;
	}

	public void setCommand(int command) {
		this.command = command;
	}

	
	
	/**
	 * @param term - the term that the message belongs to
	 * @param index - position of the command in the log 
	 * @param command - given to the cluster leader to commit
	 */
	public LogEntries(int command, int index, int term) {
		//super();
		this.term = term;
		this.index = index;
		this.command = command;
	} 
	
	@Override
	public String toString() {
		return "Term: " + this.term + "Index: " + this.index + "Command: " + this.command;
	}
	
}