package smartfarm.service;
import smartfarm.dao.TaskDAO;
import smartfarm.dao.WorkerDAO;
import smartfarm.model.Task;
import smartfarm.model.Worker;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TaskService {
    private final TaskDAO taskProcess;
    private final WorkerDAO workerDAO = new WorkerDAO();

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

    // make the assign to least load worker instead of avaliable
    public void autoCreateTask(Task task){
        if(task.getTaskId()!=-1){
            throw new RuntimeException("The Task ID Already Exists");
        }
        WorkerService service = new WorkerService(workerDAO);
        List<Worker> freeWorkers = service.getAvailableWorkers();
        if(freeWorkers.isEmpty()){
            throw new RuntimeException("No Available Workers Right Now");
        }
        task.addWorker(freeWorkers.get(0).getWorkerId());
        try{
            taskProcess.save(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void advanceTaskStatus(int taskID){
        if(taskID==-1){
            throw new RuntimeException("The Task Doesn't Exist");
        }
        Task task;
        try{
            task = taskProcess.getById(taskID);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
        if(task == null){
            throw new RuntimeException("Task Not Found");
        }
        task.advanceStatus();
        try{
            taskProcess.update(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void revertTaskStatus(int taskID){
        if(taskID==-1){
            throw new RuntimeException("The Task Doesn't Exist");
        }
        Task task;
        try{
            task = taskProcess.getById(taskID);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
        if(task == null){
            throw new RuntimeException("Task Not Found");
        }
        task.revertStatus();
        try{
            taskProcess.update(task);
        }
        catch(SQLException err){
            throw new RuntimeException("Server Error! Try again later");
        }
    }

    public void updateTask(Task task){
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

    public List<Task> getActiveTasks(){
        List<Task> allTasks = getAllTasks(), available = new ArrayList<>();
        for(Task task: allTasks){
            if(!task.isOverdue()){
                available.add(task);
            }
        }
        return available;
    }

    public List<Task> getOverdueTasks(){
        List<Task> allTasks = getAllTasks(), overdue = new ArrayList<>();
        for(Task task: allTasks){
            if(task.isOverdue()){
                overdue.add(task);
            }
        }
        return overdue;
    }


    public void deleteTask(int taskID){
        try {
            taskProcess.delete(taskID);
        }
        catch (SQLException err) {
            throw new RuntimeException("Server Error! Try again later");
        }
    }
}