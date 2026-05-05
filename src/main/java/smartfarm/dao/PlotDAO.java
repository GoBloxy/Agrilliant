package smartfarm.dao;

import smartfarm.model.Plot;
import smartfarm.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class PlotDAO implements GenericDAO<Plot> {
    private final Connection conn = DBConnection.getInstance();

    // TODO: addPlot, getPlotById, getAllPlots, updatePlot, deletePlot
}
