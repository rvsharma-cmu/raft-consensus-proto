
import lib.Message;
import lib.RequestVoteArgs;
import lib.RequestVoteReply; 

public class ElectionThread extends Thread{
	
	private int startId;
	private int endId;

	private RequestVoteArgs args;
	private RaftNode node;

	
	public ElectionThread(RaftNode node, int start, int end, RequestVoteArgs args) {
		
		this.node = node; 
		this.startId = start; 
		this.endId = end; 
		this.args = args; 
	}
	
	

}
