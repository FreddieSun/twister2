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
package edu.iu.dsc.tws.examples.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Submitter;
import edu.iu.dsc.tws.api.job.Twister2Job;
import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.common.discovery.IWorkerController;
import edu.iu.dsc.tws.common.resource.AllocatedResources;
import edu.iu.dsc.tws.common.resource.WorkerComputeResource;
import edu.iu.dsc.tws.common.worker.IPersistentVolume;
import edu.iu.dsc.tws.common.worker.IVolatileVolume;
import edu.iu.dsc.tws.common.worker.IWorker;
import edu.iu.dsc.tws.examples.IntData;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.core.SchedulerContext;
import edu.iu.dsc.tws.task.api.ICompute;
import edu.iu.dsc.tws.task.api.IMessage;
import edu.iu.dsc.tws.task.api.TaskContext;
import edu.iu.dsc.tws.task.graph.DataFlowTaskGraph;
import edu.iu.dsc.tws.task.graph.GraphBuilder;
import edu.iu.dsc.tws.task.graph.GraphConstants;
import edu.iu.dsc.tws.tsched.spi.scheduler.Worker;
import edu.iu.dsc.tws.tsched.spi.scheduler.WorkerPlan;
import edu.iu.dsc.tws.tsched.spi.taskschedule.TaskSchedulePlan;
import edu.iu.dsc.tws.tsched.taskscheduler.TaskScheduler;

public class SimpleTaskGraphExample implements IWorker {

  private static final Logger LOG = Logger.getLogger(SimpleTaskGraphExample.class.getName());

  public static void main(String[] args) {
    // first load the configurations from command line and config files
    Config config = ResourceAllocator.loadConfig(new HashMap<>());

    // build JobConfig
    HashMap<String, Object> configurations = new HashMap<>();
    configurations.put(SchedulerContext.THREADS_PER_WORKER, 8);

    JobConfig jobConfig = new JobConfig();
    jobConfig.putAll(configurations);

    // build the job
    Twister2Job twister2Job = Twister2Job.newBuilder()
            .setName("basic-taskgraphJob")
            .setWorkerClass(SimpleTaskGraphExample.class.getName())
            .setRequestResource(new WorkerComputeResource(2, 1024, 1.0), 2)
            .setConfig(jobConfig)
            .build();

    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

  /**
   * Init method to submit the task to the executor
   */
  public void execute(Config cfg, int workerID, AllocatedResources resources,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {

    LOG.log(Level.INFO, "Starting the example with container id: " + resources.getWorkerId());

    TaskMapper taskMapper = new TaskMapper("task1");
    TaskReducer taskReducer = new TaskReducer("task2");
    TaskShuffler taskShuffler = new TaskShuffler("task3");
    TaskMerger taskMerger = new TaskMerger("task4");

    GraphBuilder graphBuilder = GraphBuilder.newBuilder();
    graphBuilder.addTask("task1", taskMapper);
    graphBuilder.addTask("task2", taskReducer);
    graphBuilder.addTask("task3", taskShuffler);
    graphBuilder.addTask("task4", taskMerger);

    graphBuilder.connect("task1", "task2", "Reduce");
    graphBuilder.connect("task1", "task3", "Shuffle");
    graphBuilder.connect("task2", "task3", "merger1");
    graphBuilder.connect("task3", "task4", "merger2");

    graphBuilder.setParallelism("task1", 2);
    graphBuilder.setParallelism("task2", 2);
    graphBuilder.setParallelism("task3", 2);
    graphBuilder.setParallelism("task4", 2);

    graphBuilder.addConfiguration("task1", "Ram", GraphConstants.taskInstanceRam(cfg));
    graphBuilder.addConfiguration("task1", "Disk", GraphConstants.taskInstanceDisk(cfg));
    graphBuilder.addConfiguration("task1", "Cpu", GraphConstants.taskInstanceCpu(cfg));

    graphBuilder.addConfiguration("task2", "Ram", GraphConstants.taskInstanceRam(cfg));
    graphBuilder.addConfiguration("task2", "Disk", GraphConstants.taskInstanceDisk(cfg));
    graphBuilder.addConfiguration("task2", "Cpu", GraphConstants.taskInstanceCpu(cfg));

    graphBuilder.addConfiguration("task3", "Ram", GraphConstants.taskInstanceRam(cfg));
    graphBuilder.addConfiguration("task3", "Disk", GraphConstants.taskInstanceDisk(cfg));
    graphBuilder.addConfiguration("task3", "Cpu", GraphConstants.taskInstanceCpu(cfg));

    graphBuilder.addConfiguration("task4", "Ram", GraphConstants.taskInstanceRam(cfg));
    graphBuilder.addConfiguration("task4", "Disk", GraphConstants.taskInstanceDisk(cfg));
    graphBuilder.addConfiguration("task4", "Cpu", GraphConstants.taskInstanceCpu(cfg));

    graphBuilder.addConfiguration("task1", "dataset", new ArrayList<>().add("dataset1.txt"));
    graphBuilder.addConfiguration("task2", "dataset", new ArrayList<>().add("dataset2.txt"));
    graphBuilder.addConfiguration("task3", "dataset", new ArrayList<>().add("dataset3.txt"));
    graphBuilder.addConfiguration("task4", "dataset", new ArrayList<>().add("dataset4.txt"));

    DataFlowTaskGraph dataFlowTaskGraph = graphBuilder.build();
    LOG.info("Generated Dataflow Task Graph Is:" + dataFlowTaskGraph.getTaskVertexSet());

    //For scheduling streaming task

    String jobType = "batch";
    String schedulingType = "datalocalityaware";

    TaskSchedulePlan taskSchedulePlan = null;

    if (workerID == 0) {
      if ("Batch".equalsIgnoreCase(jobType)
              && "roundrobin".equalsIgnoreCase(schedulingType)) {
        TaskScheduler taskScheduler = new TaskScheduler();
        taskScheduler.initialize(cfg);
        taskSchedulePlan = taskScheduler.schedule(dataFlowTaskGraph, createWorkerPlan(resources));
      }
    }

    Map<Integer, TaskSchedulePlan.ContainerPlan> containersMap
            = taskSchedulePlan.getContainersMap();
    for (Map.Entry<Integer, TaskSchedulePlan.ContainerPlan> entry : containersMap.entrySet()) {
      Integer integer = entry.getKey();
      TaskSchedulePlan.ContainerPlan containerPlan = entry.getValue();
      Set<TaskSchedulePlan.TaskInstancePlan> taskInstancePlans
              = containerPlan.getTaskInstances();
      //int containerId = containerPlan.getRequiredResource().getId();
      LOG.info("Container Index (Schedule Method):" + integer);
      for (TaskSchedulePlan.TaskInstancePlan ip : taskInstancePlans) {
        LOG.info("Task Id:" + ip.getTaskId() + "\tTask Index" + ip.getTaskIndex()
                + "\tTask Name:" + ip.getTaskName() + "\tContainer Index:" + integer);
      }
    }
  }

  public WorkerPlan createWorkerPlan(AllocatedResources resourcePlan) {
    List<Worker> workers = new ArrayList<>();
    for (WorkerComputeResource resource : resourcePlan.getWorkerComputeResources()) {
      Worker w = new Worker(resource.getId());
      workers.add(w);
    }

    return new WorkerPlan(workers);
  }

  /**
   * Generate data with an integer array
   *
   * @return IntData
   */
  private IntData generateData() {
    int[] d = new int[10];
    for (int i = 0; i < 10; i++) {
      d[i] = i;
    }
    return new IntData(d);
  }

  private enum Status {
    INIT,
    MAP_FINISHED,
    LOAD_RECEIVE_FINISHED,
  }

  private class TaskMapper implements ICompute {
    private static final long serialVersionUID = 3233011943332591934L;
    public String taskName = null;

    protected TaskMapper(String taskName1) {
      this.taskName = taskName1;
    }

    /**
     * Prepare the task to be executed
     *
     * @param cfg        the configuration
     * @param collection the output collection
     */
    @Override
    public void prepare(Config cfg, TaskContext collection) {

    }

    /**
     * Execute with an incoming message
     */
    @Override
    public void execute(IMessage content) {

    }
  }

  private class TaskReducer implements ICompute {
    private static final long serialVersionUID = 3233011943332591934L;
    public String taskName = null;

    protected TaskReducer(String taskName1) {
      this.taskName = taskName1;
    }

    /**
     * Prepare the task to be executed
     *
     * @param cfg        the configuration
     * @param collection the output collection
     */
    @Override
    public void prepare(Config cfg, TaskContext collection) {

    }

    /**
     * Execute with an incoming message
     */
    @Override
    public void execute(IMessage content) {

    }
  }

  private class TaskShuffler implements ICompute {
    private static final long serialVersionUID = 3233011943332591934L;
    public String taskName = null;

    protected TaskShuffler(String taskName1) {
      this.taskName = taskName1;
    }

    /**
     * Prepare the task to be executed
     *
     * @param cfg        the configuration
     * @param collection the output collection
     */
    @Override
    public void prepare(Config cfg, TaskContext collection) {

    }

    /**
     * Execute with an incoming message
     */
    @Override
    public void execute(IMessage content) {

    }
  }

  private class TaskMerger implements ICompute {
    private static final long serialVersionUID = 3233011943332591934L;
    public String taskName = null;

    protected TaskMerger(String taskName1) {
      this.taskName = taskName1;
    }

    /**
     * Prepare the task to be executed
     *
     * @param cfg        the configuration
     * @param collection the output collection
     */
    @Override
    public void prepare(Config cfg, TaskContext collection) {

    }

    /**
     * Execute with an incoming message
     */
    @Override
    public void execute(IMessage content) {

    }
  }
}




