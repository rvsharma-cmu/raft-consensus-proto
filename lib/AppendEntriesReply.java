package lib;

import java.io.Serializable;

@SuppressWarnings("serial")
public class AppendEntriesReply implements Serializable {
	
	private int term; 
	
	private boolean success; 
	
	public AppendEntriesReply(int term, boolean success) {
		
		this.term = term; 
		this.success = success; 
		
	}
	
	public int getTerm() {
		return term;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}


}
