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
package edu.iu.dsc.tws.examples.task.streaming;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Submitter;
import edu.iu.dsc.tws.api.job.Twister2Job;
import edu.iu.dsc.tws.api.task.ComputeConnection;
import edu.iu.dsc.tws.api.task.TaskGraphBuilder;
import edu.iu.dsc.tws.api.task.TaskWorker;
import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.data.api.DataType;
import edu.iu.dsc.tws.examples.internal.task.TaskUtils;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.core.SchedulerContext;
import edu.iu.dsc.tws.task.api.IFunction;
import edu.iu.dsc.tws.task.api.IMessage;
import edu.iu.dsc.tws.task.graph.DataFlowTaskGraph;
import edu.iu.dsc.tws.task.graph.OperationMode;
import edu.iu.dsc.tws.task.streaming.BaseStreamCompute;
import edu.iu.dsc.tws.task.streaming.BaseStreamSink;
import edu.iu.dsc.tws.task.streaming.BaseStreamSource;

public class MultiStageGraph extends TaskWorker {
  private static final Logger LOG = Logger.getLogger(MultiStageGraph.class.getName());

  @Override
  public void execute() {
    GeneratorTask g = new GeneratorTask();
    ReduceTask rt = new ReduceTask();
    PartitionTask r = new PartitionTask();

    TaskGraphBuilder builder = TaskGraphBuilder.newBuilder(config);
    builder.addSource("source", g, 4);
    ComputeConnection pc = builder.addCompute("compute", r, 4);
    pc.partition("source", "partition-edge", DataType.OBJECT);
    ComputeConnection rc = builder.addSink("sink", rt, 1);
    rc.reduce("compute", "compute-edge", new IFunction() {
      @Override
      public Object onMessage(Object object1, Object object2) {
        return object1;
      }
    });
    builder.setMode(OperationMode.STREAMING);

    DataFlowTaskGraph graph = builder.build();
    TaskUtils.execute(config, workerId, graph, workerController);
  }

  private static class GeneratorTask extends BaseStreamSource {
    private static final long serialVersionUID = -254264903510284748L;

    private int count = 0;

    @Override
    public void execute() {
      boolean wrote = context.write("partition-edge", "Hello");
      if (wrote) {
        count++;
        if (count % 100 == 0) {
          LOG.info(String.format("%d %d Source sent count : %d", context.getWorkerId(),
              context.taskId(), count));
        }
      }
    }
  }

  private static class ReduceTask extends BaseStreamSink {
    private static final long serialVersionUID = -254264903510284791L;
    private int count = 0;

    @Override
    public boolean execute(IMessage message) {
      count++;
      LOG.info(String.format("%d %d Reduce received count: %d", context.getWorkerId(),
          context.taskId(), count));
      return true;
    }
  }

  private static class PartitionTask extends BaseStreamCompute {
    private static final long serialVersionUID = -254264903510284798L;

    private int count = 0;

    @Override
    public boolean execute(IMessage message) {
      if (message.getContent() instanceof List) {
        count += ((List) message.getContent()).size();
        for (Object o : (List) message.getContent()) {
          context.write("compute-edge", o);
        }
      }
      LOG.info(String.format("%d %d Partition Received count: %d", context.getWorkerId(),
          context.taskId(), count));
      return true;
    }
  }

  public static void main(String[] args) {
    // first load the configurations from command line and config files
    Config config = ResourceAllocator.loadConfig(new HashMap<>());

    // build JobConfig
    HashMap<String, Object> configurations = new HashMap<>();
    configurations.put(SchedulerContext.THREADS_PER_WORKER, 8);

    // build JobConfig
    JobConfig jobConfig = new JobConfig();
    jobConfig.putAll(configurations);

    Twister2Job.Twister2JobBuilder jobBuilder = Twister2Job.newBuilder();
    jobBuilder.setJobName(MultiStageGraph.class.getName());
    jobBuilder.setWorkerClass(MultiStageGraph.class.getName());
    jobBuilder.addComputeResource(1, 512, 4);
    jobBuilder.setConfig(jobConfig);

    // now submit the job
    Twister2Submitter.submitJob(jobBuilder.build(), config);
  }
}
