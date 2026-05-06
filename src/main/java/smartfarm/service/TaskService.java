package smartfarm.service;
import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import java.sql.SQLException;
import java.util.List;

public class TaskService {
    private final TaskDAO taskProcess;
    WorkerDAO DAO = new WorkerDAO();

    public TaskService(TaskDAO taskProcess) {
        this.taskProcess = taskProcess;
    }

    public void createTask(Task task){
        if(task.getTaskId()!=-1){
            throw new RuntimeException("The Task ID Already Exists");
        }
        try{
            taskProcess.save(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void autoCreateTask(Task task){
        if(task.getTaskId()!=-1){
            throw new RuntimeException("The Task ID Already Exists");
        }
        WorkerService service = new WorkerService(DAO);
        List<Worker> freeWorkers = service.getAvailableWorkers();
        if(freeWorkers.isEmpty()){
            throw new RuntimeException("No Available Workers Right Now");
        }
        Worker freeWorker = freeWorkers.getFirst();
        task.setWorkerId(freeWorker.getUserId());
        try{
            taskProcess.save(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void advanceTaskStatus(Task task){
        if(task.getTaskId()==-1){
            throw new RuntimeException("The Task Doesn't Exist");
        }
        task.advanceStatus();
        try{
            taskProcess.update(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void revertTaskStatus(Task task){
        if(task.getTaskId()==-1){
            throw new RuntimeException("The Task Doesn't Exist");
        }
        task.revertStatus();
        try{
            taskProcess.update(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void updatetTask(Task task){
        if(task.getTaskId()==-1){
            throw new RuntimeException("The Task Doesn't Exist");
        }
        try{
            taskProcess.update(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public List<Task> getAllTasks(){
        try {
            return taskProcess.getAll();
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void deleteTask(Task task){
        try {
            taskProcess.delete(task.getTaskId());
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
