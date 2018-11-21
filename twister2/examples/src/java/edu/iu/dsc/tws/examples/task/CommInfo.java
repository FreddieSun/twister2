package edu.iu.dsc.tws.examples.task;

import java.io.*;

//-itr 1 -workers 4 -size 8 -op "reduce" -stages 8,1 -verify

public class CommInfo implements Serializable{
    private int sourceParallelism;

    private int sinkParallelism;

    private int itr;

    private int workers;

    private String operationMode;

    public CommInfo(int sourceParallelism, int sinkParallelism, int itr, int workers, String operationMode) {
        this.sourceParallelism = sourceParallelism;
        this.sinkParallelism = sinkParallelism;
        this.itr = itr;
        this.workers = workers;
        this.operationMode = operationMode;
    }

    public int getSourceParallelism() {
        return sourceParallelism;
    }

    public void setSourceParallelism(int sourceParallelism) {
        this.sourceParallelism = sourceParallelism;
    }

    public int getSinkParallelism() {
        return sinkParallelism;
    }

    public void setSinkParallelism(int sinkParallelism) {
        this.sinkParallelism = sinkParallelism;
    }

    public int getItr() {
        return itr;
    }

    public void setItr(int itr) {
        this.itr = itr;
    }

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public String getOperationMode() {
        return operationMode;
    }

    public void setOperationMode(String operationMode) {
        this.operationMode = operationMode;
    }
}