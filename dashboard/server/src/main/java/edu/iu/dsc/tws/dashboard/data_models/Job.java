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
package edu.iu.dsc.tws.dashboard.data_models;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

@Entity
public class Job {

  @Id
  private String id;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column
  private Date heartbeatTime; //job master heartbeat

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "job",
      orphanRemoval = true)
  private Set<Worker> workers = new HashSet<>();

  @ManyToOne
  private Cluster cluster;

  @Column
  @Enumerated(EnumType.STRING)
  private EntityState state;

  public EntityState getState() {
    return state;
  }

  public void setState(EntityState state) {
    this.state = state;
  }

  public Cluster getCluster() {
    return cluster;
  }

  public void setCluster(Cluster cluster) {
    this.cluster = cluster;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Date getHeartbeatTime() {
    return heartbeatTime;
  }

  public void setHeartbeatTime(Date heartbeatTime) {
    this.heartbeatTime = heartbeatTime;
  }

  public Set<Worker> getWorkers() {
    return workers;
  }

  public void setWorkers(Set<Worker> workers) {
    this.workers = workers;
  }

  @Override
  public String toString() {
    return "Job{"
        + "id='" + id + '\''
        + ", name='" + name + '\''
        + ", description='" + description + '\''
        + ", heartbeatTime=" + heartbeatTime
        + ", workers=" + workers
        + '}';
  }
}
