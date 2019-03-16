package lib;

import java.io.Serializable;

/**
 * Class 
 * @author abubber and rvsharma
 * 
 * A class that holds the log entry information
 *
 */
public class LogEntry implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public Integer term; 
	public Integer index; 
	public int getTerm() {
		return term;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public Integer getIndex() {
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

	public int command;
	
	
	/**
	 * @param term - the term that the message belongs to
	 * @param index - position of the command in the log 
	 * @param command - given to the cluster leader to commit
	 */
	public LogEntry(int term, int index, int command) {
		super();
		this.term = term;
		this.index = index;
		this.command = command;
	} 
	
	@Override
	public String toString() {
		return "Term: " + this.term + "Index: " + this.index + "Command: " + this.command;
	}
	
}
