package smartfarm.service;
import smartfarm.model.Worker;
import smartfarm.dao.WorkerDAO;

import java.sql.SQLException;

public class WorkerService {
    private final WorkerDAO workerProcess;

    public WorkerService(WorkerDAO workerProcess) {
        this.workerProcess = workerProcess;
    }

    // TODO: addWorker, getAvailableWorkers, getWorkerWorkload
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
}
