//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.checkpointmanager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.Message;

import edu.iu.dsc.tws.checkpointmanager.state_backend.FsCheckpointStreamFactory;
import edu.iu.dsc.tws.checkpointmanager.utils.CheckpointContext;
import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.common.config.Context;
import edu.iu.dsc.tws.common.net.tcp.request.MessageHandler;
import edu.iu.dsc.tws.common.net.tcp.request.RRServer;
import edu.iu.dsc.tws.common.net.tcp.request.RequestID;
import edu.iu.dsc.tws.comms.utils.KryoSerializer;
import edu.iu.dsc.tws.data.fs.Path;
import edu.iu.dsc.tws.data.fs.local.LocalDataInputStream;
import edu.iu.dsc.tws.data.fs.local.LocalFileSystem;
import edu.iu.dsc.tws.proto.checkpoint.Checkpoint;

public class TaskBarrierMonitor implements MessageHandler {

  private static final Logger LOG = Logger.getLogger(TaskBarrierMonitor.class.getName());

  private Config config;
  private RRServer rrServer;

  private List<Integer> sourceTaskList;
  private List<Integer> sinkTaskList;

  private int sourceParallelism;
  private int sinkParallelism;

  private Set<Integer> currentBarrierReceivedSourceSet;

  private Set<Integer> barrierCompleteReceivedSinkSet;

  private boolean sendBarrierFlag;
  private int currentBarrierID;

  private boolean allTaskGotRegistered;

  public TaskBarrierMonitor(Config cfg, RRServer server) {
    this.config = cfg;

    this.rrServer = server;
    this.sourceTaskList = new ArrayList<>();
    this.sinkTaskList = new ArrayList<>();

    this.sourceParallelism = 0;
    this.sinkParallelism = 0;

    this.allTaskGotRegistered = false;

    System.out.println(config.getStringValue(Context.JOB_NAME));
  }

  @Override
  public void onMessage(RequestID id, int taskId, Message message) {

    if (message instanceof Checkpoint.TaskDiscovery) {
      Checkpoint.TaskDiscovery taskDiscoveryMessage = (Checkpoint.TaskDiscovery) message;

      if (taskDiscoveryMessage.getTaskType().equals(Checkpoint.TaskDiscovery.TaskType.SOURCE)) {
        LOG.fine("Source task with ID " + taskDiscoveryMessage.getTaskID()
            + " registered with Checkpoint Manager");

        this.sourceTaskList.add(taskDiscoveryMessage.getTaskID());

        if (this.sourceParallelism == 0) {
          sourceParallelism = taskDiscoveryMessage.getParrallelism();
        }

        checkAllTaskGotRegistered();


      } else if (taskDiscoveryMessage.getTaskType()
          .equals(Checkpoint.TaskDiscovery.TaskType.SINK)) {

        LOG.fine("Sink task with ID " + taskDiscoveryMessage.getTaskID()
            + " registered with Checkpoint Manager");

        this.sinkTaskList.add(taskDiscoveryMessage.getTaskID());

        if (this.sinkParallelism == 0) {
          sinkParallelism = taskDiscoveryMessage.getParrallelism();
        }

        checkAllTaskGotRegistered();

      }

    } else if (message instanceof Checkpoint.BarrierSync) {

      LOG.fine("Source task " + taskId + " sent BarrierSync message.");
      Checkpoint.BarrierSync barrierSyncMessage = (Checkpoint.BarrierSync) message;

      if (sendBarrierFlag && allTaskGotRegistered) {

        Checkpoint.BarrierSend barrierSendMessage = Checkpoint.BarrierSend.newBuilder()
            .setSendBarrier(true)
            .setCurrentBarrierID(currentBarrierID)
            .build();

        rrServer.sendResponse(id, barrierSendMessage);

        currentBarrierReceivedSourceSet.add(barrierSyncMessage.getTaskID());

        checkAllBarrierGotSent();

      } else {

        Checkpoint.BarrierSend barrierSendMessage = Checkpoint.BarrierSend.newBuilder()
            .setSendBarrier(false)
            .setCurrentBarrierID(currentBarrierID)
            .build();

        rrServer.sendResponse(id, barrierSendMessage);
      }

    } else if (message instanceof Checkpoint.CheckpointComplete) {

      LOG.fine("Sink task " + taskId + " sent CheckpointComplete message.");

      Checkpoint.CheckpointComplete checkpointCompleteMessage
          = (Checkpoint.CheckpointComplete) message;

      if (checkpointCompleteMessage.getCheckpointComplete()
          && checkpointCompleteMessage.getCurrentBarrierID() == this.currentBarrierID) {
//        LOG.info("Checkpointing with Barrier ID " + currentBarrierID + " is completed");

        barrierCompleteReceivedSinkSet.add(checkpointCompleteMessage.getSinkID());

        checkAllSinkSentBarrierComplete();

      }
    }
  }

  private void printTaskList(List<Integer> ids, String type) {
    String temp = type + " Task IDs";
    for (Integer i : ids) {
      temp += " " + i;
    }
    LOG.info(temp);
  }

  /**
   * This will check whether all the source and sink tasks have got registered
   */
  private void checkAllTaskGotRegistered() {
    if ((sourceTaskList.size() == sourceParallelism) && (sinkTaskList.size() == sinkParallelism)) {
      printTaskList(sourceTaskList, "Source");
      printTaskList(sinkTaskList, "Sink");

      this.allTaskGotRegistered = true;

      sendBarrierFlag = true;
      currentBarrierID = 1;
      if (CheckpointContext.getCheckpointRecovery(config)) {
        try {
          currentBarrierID = readBarrierID() + 1;
        } catch (Exception e) {
          LOG.log(Level.WARNING, "could not read last acknowledge barrierID", e);
        }

      }
      currentBarrierReceivedSourceSet = new HashSet<Integer>();

      barrierCompleteReceivedSinkSet = new HashSet<Integer>();

      LOG.info("All source and sink tasks got registered");
    }

  }

  /**
   * This will make sendBarrier Conditions false so that it will not
   * emit any more barriers until previous barrier got received from the sink
   */
  private void checkAllBarrierGotSent() {
    if (currentBarrierReceivedSourceSet.size() == sourceParallelism) {
      currentBarrierReceivedSourceSet = new HashSet<Integer>();
      sendBarrierFlag = false;


      LOG.info("Barriers with Barrier ID " + currentBarrierID + " got sent from Source Tasks");
    }
  }

  private void checkAllSinkSentBarrierComplete() {
    if (barrierCompleteReceivedSinkSet.size() == sinkParallelism) {
      barrierCompleteReceivedSinkSet = new HashSet<Integer>();

      try {
        writeBarrierID(currentBarrierID);

        LOG.info("All sinks acknowledged checkpoint manager for barrier ID : " + currentBarrierID);
        //start the next checkpoint
        currentBarrierID++;
        sendBarrierFlag = true;
      } catch (Exception e) {
        LOG.log(Level.WARNING, "could not store acknowledge barrierID", e);
      }

    }
  }

  private void writeBarrierID(int barrierID) throws Exception {
    synchronized (this) {
      Path path = new Path(new File(CheckpointContext
          .getStatebackendDirectoryDefault(config)).toURI());
//      Path path = new Path("/home/kumar/statebackend/");
      Path path2 = new Path(path, config.getStringValue(Context.JOB_NAME));
      LocalFileSystem localFileSystem = new LocalFileSystem();
      FsCheckpointStreamFactory fs = new FsCheckpointStreamFactory(path2, path2,
          0, localFileSystem);
      KryoSerializer kryoSerializer = new KryoSerializer();
      byte[] checkpoint = kryoSerializer.serialize(barrierID);
      FsCheckpointStreamFactory.FsCheckpointStateOutputStream stream =
          fs.createCheckpointStateOutputStream();
      stream.initialize("Acknowledged", "BarrierIDs");
      stream.write(checkpoint);
      stream.closeWriting();
    }
  }

  private int readBarrierID() throws Exception {
    Path path = new Path(new File(CheckpointContext
        .getStatebackendDirectoryDefault(config)).toURI());
    Path path2 = new Path(path, config.getStringValue(Context.JOB_NAME));
    LocalFileSystem localFileSystem = new LocalFileSystem();
    FsCheckpointStreamFactory fs = new FsCheckpointStreamFactory(path2, path2,
        0, localFileSystem);
    FsCheckpointStreamFactory.FsCheckpointStateOutputStream stream =
        fs.createCheckpointStateOutputStream();
    LocalDataInputStream localDataReadStream = (LocalDataInputStream)
        stream.openStateHandle("Acknowledged",
            "BarrierIDs").openInputStream();
    byte[] checkpoint;
    synchronized (this) {
      checkpoint = stream.readCheckpoint(localDataReadStream);
    }
    KryoSerializer kryoSerializer = new KryoSerializer();
    return (int) kryoSerializer.deserialize(checkpoint);

  }


}
