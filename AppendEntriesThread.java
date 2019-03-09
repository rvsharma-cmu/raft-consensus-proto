import java.rmi.RemoteException;

import lib.AppendEntriesArgs;
import lib.AppendEntryReply;
import lib.ApplyMsg;
import lib.Message;
import lib.MessageType; 

public class AppendEntriesThread extends Thread{
	
	RaftNode node; 
	int startId; 
	int endId; 
	Message AppendEntryMessage;
	Message AppendEntryReplyMessage;
	AppendEntriesArgs args; 
	
	public AppendEntriesThread(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {
		
		this.node = node; 
		this.startId = startID; 
		this.endId = endID; 
		this.args = arguments; 
	}
	
	@Override
    public void run(){
        try{
//
            /* Create Message Packet */
            byte[] payload = BytesUtil.serialize(this.args);
            this.AppendEntryMessage = new Message(MessageType.AppendEntriesArgs, this.node.getId(), this.endId, payload);

//            System.out.println(System.currentTimeMillis() + " Node" + this.node.getId() + " Sent AppendEntries RPC to Node" + dest_id + " " + args.prevLogIndex + " " + args.prevLogTerm + " " + args.entries.size());
            /* Applying instance of transportLib, calling sendMessage.*/
            try {
                AppendEntryReplyMessage = this.node.lib.sendMessage(AppendEntryMessage);
            }
            catch(Exception e){

                //System.out.println(System.currentTimeMillis()+" Node "+startId+" AppendEntries to Node "+endId + " Failed to SenT!" + args.prevLogIndex+" "+args.prevLogTerm+" "+args.entries.size());

                this.join();
                return;
            }

            /* The Reply might be null and need to check before Use */
            if(AppendEntryReplyMessage == null){
//                        System.out.println(System.currentTimeMillis()+" Node "+this.node.getId()+" Append RPC to Node "+dest_id + " Return NULL!" + args.prevLogIndex+" "+args.prevLogTerm+" "+args.entries.size());
                this.join();
                return;
            }
            else{
                this.node.lock.lock();

                AppendEntryReply reply = (AppendEntryReply) BytesUtil.deserialize(AppendEntryReplyMessage.getBody());


                if(reply.term > node.nodeState.currentTerm){
                    /* If RPC response contains term T > currentTerm: set currentTerm = T, convert to follower */
                    node.nodeState.currentTerm = reply.term;
//                          System.out.print(System.currentTimeMillis() + "Node " + this.node.getId() +" Role From "+this.node.nodeState.get_role()+" ");
                    node.nodeState.setNodeState(lib.State.States.FOLLOWER);
//                          System.out.println(System.currentTimeMillis() + "To " + this.node.nodeState.get_role());
                    node.nodeState.votedFor = null;
                    node.numOfVotes = 0;

                    node.lock.unlock();

                    this.join();
                    return;
                }

                if(reply.success == false){
                    /* AppendEntries Rejected, Must due to Consistency Check Failure
                       decrement nextIndex and retry
                     */
//                    System.out.println(System.currentTimeMillis()+" Node "+this.node.getId()+" Append RPC to Node "+dest_id + " rejected!" + args.prevLogIndex + " " + args.prevLogTerm + " " + args.entries.size());
                    node.nodeState.nextIndex[endId] = node.nodeState.nextIndex[endId] - 1;

                    node.lock.unlock();

                    this.join();
                    return;
                }
                else {
                    /*
                       Append Entries Succesfully!
                       update nextIndex and matchIndex
                     */

//                    System.out.println(System.currentTimeMillis() + " Node " + this.node.getId() + " Append RPC to Node " + dest_id + " Succeeded!");
                    if (args.entries.size() > 0) {
//                            for (LogEntries e : args.entries) {
//                                e.print();
//                            }
                    	int length = node.nodeState.getLog().size();
                    	
                        node.nodeState.matchIndex[endId] = args.entries.get(args.entries.size() - 1).getTerm();
                        node.nodeState.nextIndex[endId] = args.entries.get(args.entries.size() - 1).getIndex() + 1;

                        /*
                             If there exists an N such that N > commitIndex,
                                a majority of matchIndex[i] ≥ N, and log[N].term == currentTerm:
                                        set commitIndex = N (§5.3, §5.4).
                         */

                        int lastLogIndex = node.nodeState.getLog().get(length - 1) == null ? 0 : node.nodeState.getLog().get(length - 1).getIndex();

                        for (int N = this.node.nodeState.commitIndex + 1; N <= lastLogIndex; N++) {
                            int match_count = 0;
                            for (int i = 0; i < this.node.numPeers; i++) {
                                if (this.node.nodeState.matchIndex[i] >= N) {
                                    match_count++;
                                }
                            }
                                /*
                                    Only log entries from the leader’s current term are committed by counting replicas;
                                    Once an entry from the current term has been committed in this way,
                                    Then all prior entries are committed indirectly
                                 */
                            if (match_count > this.node.majorityVotes && (this.node.nodeState.getLog().get(N - 1).getTerm() == this.node.nodeState.currentTerm)) {
                                /* Apply Entry to State Machine */
                                for (int k = this.node.nodeState.commitIndex + 1; k <= N; k++) {
                                    ApplyMsg msg = new ApplyMsg(this.node.getId(), k, this.node.nodeState.log.get(k - 1).command, false, null);
                                    try {
                                        this.node.lib.applyChannel(msg);
//                                        System.out.println(System.currentTimeMillis() + "Node:" + this.node.getId() + " Role: " + this.node.nodeState.get_role() + " index: " + this.node.nodeState.log.get(k - 1).index + " term: " + this.node.nodeState.log.get(k - 1).term + " command: " + this.node.nodeState.log.get(k - 1).command + " Has been Committed");
                                    } catch (RemoteException e) {
                                        e.printStackTrace();

                                        this.node.lock.unlock();

                                        this.join();
                                        return;
                                    }
                                }
                                this.node.nodeState.commitIndex = N;
                                this.node.nodeState.lastApplied = N;
                            }

                        }
                    }
                }
            }

            node.lock.unlock();

            this.join();
            return;
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
