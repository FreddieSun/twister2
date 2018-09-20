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
package edu.iu.dsc.tws.examples.internal.task.batch;

import java.util.List;
import java.util.logging.Logger;

import edu.iu.dsc.tws.api.task.ComputeConnection;
import edu.iu.dsc.tws.comms.api.Op;
import edu.iu.dsc.tws.data.api.DataType;
import edu.iu.dsc.tws.examples.basic.comms.JobParameters;
import edu.iu.dsc.tws.examples.internal.task.BenchTaskWorker;


public class BTReduceExample extends BenchTaskWorker {
  private static final Logger LOG = Logger.getLogger(BTReduceExample.class.getName());
  private static final String SOURCE = "source";
  private static final String SINK = "sink";
  private static final String EDGE = "edge";
  private static int psource = 4;
  private static int psink = 1;
  private static final Op OPERATION = Op.SUM;
  private static final DataType DATA_TYPE = DataType.INTEGER;

  @Override
  public void intialize() {
    jobParameters = JobParameters.build(config);
    List<Integer> taskStages = jobParameters.getTaskStages();
    psource = taskStages.get(0);
    psink = taskStages.get(1);
    initialize(SOURCE, SINK, EDGE, psource, psink, OPERATION, DATA_TYPE);
  }

  @Override
  public void run(ComputeConnection computeConnection) {
    computeConnection.reduce(SOURCE, EDGE,
        OPERATION, DATA_TYPE);
  }
}