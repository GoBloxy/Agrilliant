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
            throw new RuntimeException("The Worker ID Exists Already");
        }
        try{
            workerProcess.save(worker);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error Try Again Later");
        }
    }

    public ArrayList<Worker> getAvailableWorkers(){
        ArrayList<Worker> allWorkers;
        try {
            allWorkers = workerProcess.getAll();
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error Try Again Later");
        }
        ArrayList<Worker> avaliableWorkers = new ArrayList<>();
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
            throw new RuntimeException("Server Error Try Again Later");
        }
        return worker.getActiveTaskCount();
    }

    public int getWorkerWorkloadByEmail(String email){
        Worker worker;
        try {
            worker = workerProcess.getByEmail(email);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error Try Again Later");
        }
        return worker.getActiveTaskCount();
    }

}