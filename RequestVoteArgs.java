

import java.io.Serializable;

/**
 * This class is a wrapper for packing all the arguments that you might use in
 * the RequestVote call, and should be serializable to fill in the payload of
 * Message to be sent.
 *
 */
public class RequestVoteArgs implements Serializable{
	

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public int terms;
	public int candidateId; 
	public int lastLogIndex; 
	public int lastLogTerm; 

//	public RequestVoteArgs() {
//		this(0, 0, 0, 0);
//    }
//	
	public RequestVoteArgs(int term, int candidateId, int lastLogIndex, int lastLogTerm) {
		
		this.terms = term;
		this.candidateId = candidateId; 
		this.lastLogIndex = lastLogIndex; 
		this.lastLogTerm = lastLogTerm;
	}
}
