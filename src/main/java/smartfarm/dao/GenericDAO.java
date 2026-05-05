package smartfarm.dao;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public interface GenericDAO<T> {
    void save(T item) throws SQLException;
    T getById(int id) throws SQLException;
    ArrayList<T> getAll() throws SQLException;
    void update(T item) throws SQLException;
    void delete(int id) throws SQLException;
}
