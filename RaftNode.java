import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.*;

import lib.*;

public class RaftNode implements MessageHandling, Runnable {
    private int id;
    
    boolean receivedRequest; 
    public static TransportLib lib;
    public int numPeers;
    
    private int port; 
    	
	// random method for generating random heartbeat timeouts
	Random random = new Random();
	int timeout; 
	
	// state variable for this node 
	
	State nodeState;
	public int numOfVotes; 
	public int majorityVotes; 
	public ReentrantLock lock; 
	
	private Thread thread; 

    public RaftNode(int port, int id, int numPeers) {
        this.id = id;
        this.numPeers = numPeers;
        this.port = port; 
        lib = new TransportLib(port, id, this);
        
        thread = new Thread(this); 
        thread.start();
        
        receivedRequest = false; 
        
        lock = new ReentrantLock(); 
        
        nodeState = new State(numPeers);
        majorityVotes = numPeers / 2; 
        
        numOfVotes = 0; 
        
    }

    /*
     *call back.
     */
    @Override
    public StartReply start(int command) {
        return null;
    }

    @Override
    public GetStateReply getState() {
    	
    	GetStateReply reply = new GetStateReply(nodeState.getCurrentTerm(), 
    			this.nodeState.getNodeState() == State.States.LEADER);
        return reply;
    }

    @Override
    public Message deliverMessage(Message message) {
        return null;
    }

    //main function
    public static void main(String args[]) throws Exception {
        if (args.length != 3) throw new Exception("Need 2 args: <port> <id> <num_peers>");
        //new usernode
        System.out.println(Integer.parseInt(args[0]));
        System.out.println(Integer.parseInt(args[1]));
        System.out.println(Integer.parseInt(args[2]));
        RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }
    
    public int getId() {
    	return this.id; 
    }
    
    public Vector<LogEntry> retrieveLogs (ArrayList<LogEntry> serverEntries, int index) {
    	
    	Vector<LogEntry> resultLogs = new Vector<LogEntry>(); 
    	
    	int logLength = serverEntries.size(); 
    	
    	for(int i = index - 1; i < logLength && logLength > index; i++) {
    		resultLogs.add(serverEntries.get(i));
    	}
    	
    	return resultLogs; 
    }

	@Override
	public void run() {
		
		while(true) {
			if(nodeState!=null) {
			if(nodeState.getNodeState() == State.States.LEADER) {
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				this.lock.lock(); 
				sendHeartbeats();
				this.lock.unlock();
				
			} else {
				
				timeout = random.nextInt(450) + (900 - 450); 
				
				synchronized(nodeState) {
					try {
						nodeState.wait(timeout);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				if(nodeState.getNodeState() != State.States.LEADER) {
					
					if(receivedRequest) {
						receivedRequest = false; 
						continue; 
					}
					this.lock.lock(); 
					nodeState.setNodeState(State.States.CANDIDATE);
					startElection(); 
					this.lock.unlock();
					
				}
			}
		}
		}
		
	}
	
	public void sendHeartbeats() {
		
		if(this.nodeState.getNodeState() != State.States.LEADER) {
			return; 
		} else {
			int threadNum = 0; 
			
			while(threadNum < this.numPeers) {
				
				int nextIndex = nodeState.getNextIndex(threadNum);
				Vector<LogEntry> logEntries = retrieveLogs(nodeState.getLog(), 
						nextIndex);
						
				int prevIndex = nextIndex - 1; 
				int prevTerm; 
				if(prevIndex != 0) {
					prevTerm = nodeState.getLog().get(prevIndex - 1).getTerm();
					AppendEntriesArgs entries = new AppendEntriesArgs(nodeState.getCurrentTerm(), 
							id, prevIndex, prevTerm, logEntries, nodeState.getCommitIndex());
					
					AppendEntriesThread thread = new AppendEntriesThread(this, id, threadNum, entries);
					thread.start();
					
				} else 
					prevTerm = 0; 
				
				threadNum++; 
			}
		}
	}
	
	public void startElection() {
		
		int lastIndex = 0, lastTerm = 0, threadNumber = 0; 
		
		numOfVotes = 0; 
		nodeState.setCurrentTerm(nodeState.getCurrentTerm() + 1);
		
		nodeState.setVotedFor(id);
		
		numOfVotes++; 
		
		timeout = random.nextInt(450) + (900 - 450);
		
		
		int length = nodeState.getLog().size();
		System.out.println(length);
		if(nodeState != null && !nodeState.getLog().isEmpty()) {
		if(nodeState.getLog().get(length - 1) == null) {
			lastIndex = 0; 
			lastTerm = 0; 
		} else {
			lastIndex = nodeState.getLog().get(length - 1).getIndex();
			lastTerm = nodeState.getLog().get(length - 1).getTerm(); 
		}
		}
		while(threadNumber < numPeers) {
			RequestVoteArgs args = new RequestVoteArgs(nodeState.getCurrentTerm(), id, lastIndex, lastTerm);
			
			ElectionThread electionThread = new ElectionThread(this, id, threadNumber, args);
			
			electionThread.start();
			threadNumber++;
		}
	}
	
    public RequestVoteReply requestVoteHandle(RequestVoteArgs req_args){
        RequestVoteReply req_vote_reply;
        boolean if_granted = false;

        this.lock.lock();

        /*
            1. Reply false if term < currentTerm
         */
        if(req_args.terms < this.nodeState.currentTerm){
            System.out.println(System.currentTimeMillis()+" Request Vote From Node" +req_args.candidateId+" Term: "+req_args.terms+" To Node "+this.id+" Term "+this.nodeState.currentTerm);
            req_vote_reply = new RequestVoteReply(this.nodeState.currentTerm, if_granted);

            this.receivedRequest = true;
            this.lock.unlock();

            return req_vote_reply;
        }
        /*
            2. If votedFor is null or candidateId,
               and
               candidate’s log is at least as up-to-date as receiver’s log, grant vote
         */
        // TODO: Here candidate's log and current node's log 's comparision regarding 'up-to-date' Hasn't Done
        /* If RPC request contains term T > currentTerm: set currentTerm = T, convert to follower  */
        if(req_args.terms > this.nodeState.currentTerm){
            this.nodeState.currentTerm = req_args.terms;
//            System.out.print(System.currentTimeMillis() + "Node " + this.getId() +" Role From "+this.node_state.get_role());
            this.nodeState.setNodeState(State.States.FOLLOWER);
//            System.out.println(System.currentTimeMillis() + "To " + this.node_state.get_role());
            this.nodeState.votedFor = null; // New Term has Began
            this.numOfVotes = 0;
        }
        if(this.nodeState.votedFor == null || this.nodeState.votedFor == req_args.candidateId){
            /*
                Up-to-Date Compare
                    I: If the logs have last entries with different terms, then the log with the later term is more up-to-date.
                    II: If the logs end with the same term, then whichever log is longer is more up-to-date.
             */
        	
        	int length = nodeState.getLog().size();
            int curLastLogIndex = nodeState.getLog().get(length - 1) == null ? 0 :  nodeState.getLog().get(length - 1).getIndex();
            int curLastLogTerm = nodeState.getLog().get(length - 1) == null ? 0 :  nodeState.getLog().get(length - 1).getTerm();
            if(curLastLogTerm != req_args.lastLogTerm){
                if(curLastLogTerm <= req_args.lastLogTerm){
                    /* candidate’s log is at least as up-to-date as receiver’s log, grant vote */
                    if_granted = true;
                    this.nodeState.votedFor = req_args.candidateId;
                }
            }
            else{
                if(curLastLogIndex <= req_args.lastLogIndex){
                    if_granted = true;
                    this.nodeState.votedFor = req_args.candidateId;
                }
            }
//            if_granted = true;
//            this.node_state.votedFor = req_args.candidateId;
        }
        req_vote_reply = new RequestVoteReply(this.nodeState.currentTerm, if_granted);

        this.receivedRequest = true;
        this.lock.unlock();
        return req_vote_reply;
    }
}
