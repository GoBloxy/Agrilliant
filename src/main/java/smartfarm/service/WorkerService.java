package smartfarm.service;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WorkerService {
    private final WorkerDAO workerProcess;
    private final TaskDAO taskDAO;

    public WorkerService(WorkerDAO workerProcess) {
        this.workerProcess = workerProcess;
        this.taskDAO = new TaskDAO();
    }

    public WorkerService(WorkerDAO workerProcess, TaskDAO taskDAO) {
        this.workerProcess = workerProcess;
        this.taskDAO = taskDAO;
    }

    public void addWorker(Worker worker){
        if(worker.getWorkerId() != -1){
            throw new RuntimeException("The Worker ID Already Exists");
        }
        try{
            workerProcess.save(worker);
        }
        catch (SQLException err){
            err.printStackTrace();
            throw new RuntimeException("Server Error: " + err.getMessage());
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
        List<Task> allTasks = getAllTasks();
        List<Worker> availableWorkers = new ArrayList<>();
        for(Worker worker : allWorkers){
            if(worker.isAvailable(allTasks)){
                availableWorkers.add(worker);
            }
        }
        return availableWorkers;
    }

    public int getWorkerWorkloadByID(int workerID){
        Worker worker;
        try {
            worker = workerProcess.getById(workerID);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
        if (worker == null) {
            throw new RuntimeException("Worker Not Found");
        }
        return worker.getActiveTaskCount(getAllTasks());
    }

    public List<Worker> getWorkersByManager(int managerId){
        try {
            return workerProcess.getByManager(managerId);
        }
        catch (SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    private List<Task> getAllTasks() {
        try {
            List<Task> tasks = taskDAO.getAll();
            return tasks != null ? tasks : new ArrayList<>();
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public void deleteWorker(int workerID){
        try {
            workerProcess.delete(workerID);
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }
}
