
import lib.Message;
import lib.MessageType;
import lib.RequestVoteArgs;
import lib.RequestVoteReply; 

public class ElectionThread extends Thread{
	
	private int startId; 
	private int endId; 
	
	private RequestVoteArgs args; 
	 private Message RequestVoteMessage;
	    private Message RequestVoteReplyMessage;
	private RaftNode node; 
	private RequestVoteReply reply;
	
	public ElectionThread(RaftNode node, int start, int end, RequestVoteArgs args) {
		
		this.node = node; 
		this.startId = start; 
		this.endId = end; 
		this.args = args; 
	}
	
	@Override
    public void run(){

	try{
        /* Create Message Packet */
       byte[] payload = BytesUtil.serialize(this.args);
       this.RequestVoteMessage = new Message(MessageType.RequestVoteArgs, this.startId, this.endId, payload);
       /* Applying instance of transportLib, calling sendMessage.
          The sendMessage will block until there is a reply sent back.
        */
       try {
           RequestVoteReplyMessage = node.lib.sendMessage(RequestVoteMessage);
       } catch (Exception e){
           e.printStackTrace();

           this.join();
           return;
       }
//       System.out.println("Election Thread Started!");
//       this.node.nodeState.set_role(lib.State.state.leader);
//       System.out.println("Election Thread End!");
       /* The Reply might be null and need to check before Use */
       if(RequestVoteReplyMessage == null){
           /* End this Election Thread */
           this.join();
           return;
       }
       this.reply = (RequestVoteReply) BytesUtil.deserialize(RequestVoteReplyMessage.getBody());

       this.node.lock.lock();

       if(reply.getTerm() > node.nodeState.currentTerm){
           /* If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower */
           node.nodeState.currentTerm = reply.getTerm();
//           System.out.print(System.currentTimeMillis() + "Node " + this.node.getId() +" Role From "+this.node.nodeState.get_role());
           node.nodeState.setNodeState(lib.State.States.FOLLOWER);
//           System.out.println(System.currentTimeMillis() + "To " + this.node.nodeState.get_role());
           node.nodeState.votedFor = null;
           node.numOfVotes = 0;
       }
       else if(reply.getTerm() < node.nodeState.currentTerm){
           /* Reply From Former Term, Ignore it */

           this.node.lock.unlock();

           this.join();
           return;
       }
       else{
           /*
               Here we need to Make Sure that Current Raft Node is still Candidate
               Because During ELection, If AppendEntries RPC received from new leader:
                   This Server needs to convert to follower
            */
           synchronized (node.nodeState) {
               if (reply.isVoteGranted() && (node.nodeState.getNodeState() == lib.State.States.CANDIDATE)) {
                   node.numOfVotes++;
                   node.receivedRequest = true;
               /* If votes received from majority of servers: become leader */
                   if (node.numOfVotes > node.majorityVotes) {
                       node.nodeState.setNodeState(lib.State.States.LEADER);
                   /* Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server; */
                   /* Initialize 'nextIndex' */
                       int length = node.nodeState.getLog().size();
                       int prevLastIndex = node.nodeState.getLog().get(length - 1) == null ? 0 : node.nodeState.getLog().get(length - 1).getIndex();

                       for(int i=0; i<node.numPeers; i++){
                    	   if(node != null && !node.nodeState.getLog().isEmpty()) {
                    			if(node.nodeState.getLog().get(length - 1) == null) {
                           node.nodeState.nextIndex[i] = prevLastIndex + 1;
                       }

                       node.sendHeartbeats();
                       System.out.println(System.currentTimeMillis() + " Node" + node.getId() + " Is Leader Now " + "Term: " + node.nodeState.currentTerm + " Votes: " + node.numOfVotes);
                       /* Wake Up Immediately */
                       node.nodeState.notify();
                   }
               }
           }
       }
           }
       }
       this.node.lock.unlock();

       this.join();
       return;
   
       
	}
   catch(Exception e){
       e.printStackTrace();
   }
}
}
