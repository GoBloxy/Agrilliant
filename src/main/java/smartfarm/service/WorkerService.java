package smartfarm.service;
import smartfarm.model.Worker;
import smartfarm.dao.WorkerDAO;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WorkerService {
    private final WorkerDAO workerProcess;

    public WorkerService(WorkerDAO workerProcess) {
        this.workerProcess = workerProcess;
    }

    public void addWorker(Worker worker){
        if(worker.getUserId() != -1){
            throw new RuntimeException("The Worker ID Already Exists");
        }
        try{
            workerProcess.save(worker);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void updateWorkerData(Worker worker){
        try {
            workerProcess.update(worker);
        } catch (SQLException e) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Worker> getAllWorkers(){
        List<Worker> allWorkers;
        try {
            allWorkers = workerProcess.getAll();
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
        return allWorkers;
    }

    public List<Worker> getAvailableWorkers(){
        List<Worker> allWorkers = getAllWorkers();
        List<Worker> avaliableWorkers = new ArrayList<>();
        for(Worker worker:allWorkers){
            if(worker.isAvailable()){
                avaliableWorkers.add(worker);
            }
        }
        return avaliableWorkers;
    }
    public int getWorkerWorkloadByID(int id){
        Worker worker;
        try {
            worker = workerProcess.getById(id);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
        if (worker == null) {
            throw new RuntimeException("Worker not found");
        }
        return worker.getActiveTaskCount();
    }

    public int getWorkerWorkloadByEmail(String email){
        Worker worker;
        try {
            worker = workerProcess.getByEmail(email);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
        if (worker == null) {
            throw new RuntimeException("Worker not found");
        }
        return worker.getActiveTaskCount();
    }

    public void deleteWorker(Worker worker){
        try {
            workerProcess.delete(worker.getUserId());
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }
}


/*
    Todo:
    1- Fix the foreign keys to be arrays for both worker and task
    2- propagate all the exceptions to the controller to better handle i
    3- the error handling here will be only about propagating the right custom error exception

 */