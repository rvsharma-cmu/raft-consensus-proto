import lib.Message;
import lib.RequestVoteArgs;

public class ThreadUtility extends Thread{

	 int sourceId;
	 int destId;

	 RequestVoteArgs requestVoteArgs;
	 RaftNode node;
	 Message requestMessage;
	 Message replyMessage;
	
	
	
	
	
	public void terminateThread() {
		try {
			this.join();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
}
