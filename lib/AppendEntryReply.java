package lib;

import java.io.Serializable;

public class AppendEntryReply implements Serializable {
	
	public int term; 
	public boolean success; 
	
	public AppendEntryReply(int term, boolean success) {
		
		this.term = term; 
		this.success = success; 
		
	}

}
