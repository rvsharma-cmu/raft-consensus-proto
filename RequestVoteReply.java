

import java.io.Serializable;
/**
 * This class is a wrapper for packing all the result information that you
 * might use in your own implementation of the RequestVote call, and also
 * should be serializable to return by remote function call.
 *
 */
public class RequestVoteReply implements Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	int term; 
	private boolean voteGranted; 

	public RequestVoteReply(int term, boolean voteGranted) {
    	this.term = term;
    	this.voteGranted = voteGranted; 
    }

	/**
	 * Get the term value of the server
	 * @return term value
	 */
	public int getTerm() {
		return term;
	}

	/** 
	 * set the term value to the argument 
	 * @param term  argument which must be set as the term value 
	 */
	public void setTerm(int term) {
		this.term = term;
	}

	public boolean isVoteGranted() {
		return voteGranted;
	}

	public void setVoteGranted(boolean voteGranted) {
		this.voteGranted = voteGranted;
	}
	
	
}
