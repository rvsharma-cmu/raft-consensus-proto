package lib;

import java.io.Serializable;

@SuppressWarnings("serial")
public class AppendEntriesReply implements Serializable {
	
	public int term; 
	public boolean success; 
	
	public AppendEntriesReply(int term, boolean success) {
		
		this.term = term; 
		this.success = success; 
		
	}

}
